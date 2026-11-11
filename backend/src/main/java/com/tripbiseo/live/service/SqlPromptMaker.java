package com.tripbiseo.live.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * ④ AI에게 묻기: 사용자 말을 SQL로 바꿔 달라고 Gemini에 보낼 글.
 * 표·함수·빈칸·규칙·예시를 알려 주고 JSON(SqlAnswer)으로만 답하게 해요.
 * 좌표·회원 정보는 넣지 않아요 (빈칸 이름만, 실제 값은 서버가 넣음).
 * 예시 SQL은 SqlChecker를 통과해야 해요 (SqlPromptMakerTest가 확인).
 */
@Component
public class SqlPromptMaker {

    /** 예시: {사용자 말, SQL (못 찾는 질문이면 null), 설명 또는 못 찾는 이유}. 11/11: 특징 키워드·업종 묶음·메뉴 예시로 바꿈 */
    static final List<String[]> EXAMPLES = List.of(
        new String[] {"가는 길에 5분 안에 들를 카페",
            "SELECT id FROM ask_place WHERE kind = 'cafe' AND detour_minutes(geom, :route) <= 5 "
                + "ORDER BY rating_adj DESC LIMIT 10",
            "가는 길에서 5분 안에 들를 수 있는 카페를 평점 순으로 찾았어요"},
        new String[] {"다음 일정 전에 다녀올 수 있는 디저트집",
            "SELECT id FROM ask_place WHERE (biz_type ILIKE '%디저트%' OR biz_group = '베이커리') "
                + "AND detour_minutes(geom, :route) + 30 <= :slack ORDER BY rating_adj DESC LIMIT 10",
            "들러서 30분 머물러도 다음 일정에 늦지 않는 디저트집을 찾았어요"},
        new String[] {"근처 와이파이 되는 조용한 카페",
            "SELECT id FROM ask_place WHERE kind = 'cafe' AND ST_DWithin(geom, :me, 500) "
                + "AND amenities ILIKE '%무선 인터넷%' "
                + "AND (keywords ILIKE '%차분한 분위기예요%' OR keywords ILIKE '%대화하기 좋아요%') "
                + "ORDER BY rating_adj DESC LIMIT 10",
            "500m 안 와이파이 되는 카페 중 방문자들이 차분하고 대화하기 좋다고 꼽은 곳이에요"},
        new String[] {"다음 장소 근처 리뷰 많은 고깃집",
            "SELECT id FROM ask_place WHERE biz_group = '고기·구이' AND ST_DWithin(geom, :next, 500) "
                + "ORDER BY review_count DESC LIMIT 10",
            "다음 장소 500m 안 고깃집을 리뷰 많은 순으로 찾았어요"},
        new String[] {"아이랑 갈 만한 식당 가까운 데",
            "SELECT id FROM ask_place WHERE kind = 'food' AND ST_DWithin(geom, :me, 500) "
                + "AND (keywords ILIKE '%아이와 가기 좋아요%' OR amenities ILIKE '%유아의자%') "
                + "ORDER BY ST_Distance(geom, :me) LIMIT 10",
            "500m 안 식당 중 아이와 가기 좋다고 꼽혔거나 유아의자가 있는 곳을 가까운 순으로 찾았어요"},
        new String[] {"근처 만원 이하 파스타",
            "SELECT id FROM ask_place WHERE ST_DWithin(geom, :me, 500) "
                + "AND id IN (SELECT place_id FROM ask_menu WHERE name ILIKE '%파스타%' AND price <= 10000) "
                + "ORDER BY rating_adj DESC LIMIT 10",
            "500m 안에서 메뉴에 만원 이하 파스타가 있는 곳을 찾았어요"},
        new String[] {"지금 웨이팅 없는 식당", null,
            "웨이팅·혼잡도는 실시간 정보가 없어서 찾을 수 없어요"});

    /**
     * Gemini에 보낼 글
     * @param message     사용자 말
     * @param usable      지금 값이 있는 빈칸 (route·me·next·slack 중). 대중교통 구간이면 route가 없음
     * @param retryReason 방금 쓴 SQL이 검사에서 떨어진 이유 (처음 물을 때는 null)
     */
    public String makePrompt(String message, Set<String> usable, String retryReason) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("여행 중인 사용자의 말을 PostgreSQL(PostGIS) SELECT 문 하나로 바꿔. JSON으로만 답해.\n\n");
        prompt.append(tableText()).append('\n');
        prompt.append(functionText()).append('\n');
        prompt.append(placeholderText(usable)).append('\n');
        prompt.append(wordText()).append('\n');
        prompt.append(ruleText()).append('\n');
        prompt.append(answerText()).append('\n');
        prompt.append(exampleText()).append('\n');
        prompt.append("사용자 말: \"").append(clean(message)).append("\"\n");
        if (retryReason != null) {
            prompt.append("\n방금 쓴 SQL은 쓸 수 없어: \"").append(retryReason).append("\". 규칙대로 다시 써.\n");
        }
        return prompt.toString();
    }

    // ---------------------------------------------------------------- 글 조각

    /** 쓸 수 있는 표 (18_ask_place.sql의 뷰). 11/11: biz_group·keywords 칸, ask_menu 표 */
    private String tableText() {
        return "[표]\n"
             + "ask_place: 장소\n"
             + "  id, name,\n"
             + "  kind(종류: cafe | food | bar | tour | culture | shop | stay | etc. 디저트·베이커리는 cafe, 술집은 bar),\n"
             + "  biz_group(업종 묶음: 한식 / 고기·구이 / 해산물 / 일식 / 중식 / 양식 / 아시아·외국 / 패스트푸드 / 분식 / 뷔페 / "
             + "카페·디저트 / 베이커리 / 술집 / 관광 / 문화 / 쇼핑 / 숙박. 모르면 NULL),\n"
             + "  biz_type(세부 종류 글 예: 카페,디저트 / 중식당 / 마라탕 / 스파게티,파스타전문 / 돼지고기구이 / 이자카야),\n"
             + "  rating_adj(보정 평점, 대부분 4.5~4.9), review_count(리뷰 수),\n"
             + "  amenities(가게 정보 편의시설 글: 무선 인터넷, 포장, 예약, 단체 이용 가능, 주차, 유아의자, 반려동물 동반, 노키즈존),\n"
             + "  keywords(방문자들이 이 가게의 특징으로 많이 고른 키워드를 \", \"로 이은 글. 예: 집중하기 좋아요, 뷰가 좋아요. "
             + "영등포 가게만 있고 나머지는 NULL),\n"
             + "  geom(위치)\n"
             + "ask_menu: 메뉴 (영등포 가게만)\n"
             + "  place_id(ask_place.id), name(메뉴 이름), price(원), is_main(대표 메뉴 true/false)\n"
             + "ask_review: 리뷰 (장소당 많아야 10개)\n"
             + "  place_id(ask_place.id), content(리뷰 글)\n";
    }

    private String functionText() {
        return "[함수]\n"
             + "detour_minutes(geom, :route): 가는 길에서 들르면 몇 분 더 걸리는지\n"
             + "ST_DWithin(geom, 빈칸, 미터): 몇 m 안인지 / ST_Distance(geom, 빈칸): 거리(m)\n"
             + "COUNT, COALESCE, LOWER, ROUND, LEAST, GREATEST\n";
    }

    /** 빈칸 설명 + 지금 쓸 수 있는 빈칸 */
    private String placeholderText(Set<String> usable) {
        return "[빈칸] 좌표 숫자는 쓰지 말고 이 이름만 써. 값은 서버가 넣어\n"
             + ":route 지금 가는 길 / :me 내 위치 / :next 다음 장소 / :slack 다음 일정까지 여유(분)\n"
             + "지금 쓸 수 있는 빈칸: " + usableText(usable) + "\n"
             + "가는 길(:route)을 쓸 수 없으면 다음 장소(:next)나 내 위치(:me) 근처로 찾아\n";
    }

    /** 사람 말 → keywords에 쓸 키워드 (11/11). 가벼운 모델도 같은 말에 같은 SQL을 쓰게 정해 둠 */
    private String wordText() {
        return "[단어장] 분위기·상황 말은 keywords에서 이 키워드로 찾아 (여러 개면 OR)\n"
             + "조용한·차분한 → 차분한 분위기예요, 대화하기 좋아요\n"
             + "카공·공부·작업 → 집중하기 좋아요\n"
             + "오래 있기 → 오래 머무르기 좋아요, 좌석이 편해요\n"
             + "뷰·전망 → 뷰가 좋아요\n"
             + "사진·인스타 → 사진이 잘 나와요, 인테리어가 멋져요\n"
             + "아이랑·애들 → 아이와 가기 좋아요 (amenities 유아의자도 OR)\n"
             + "혼밥·혼자 → 혼밥하기 좋아요 (술집이면 혼술하기 좋아요)\n"
             + "단체·회식·모임 → 단체모임 하기 좋아요 (amenities 단체 이용 가능도 OR)\n"
             + "데이트·기념일 → 특별한 날 가기 좋아요\n"
             + "가성비·싼 → 가성비가 좋아요\n"
             + "야외·테라스 → 야외공간이 멋져요\n";
    }

    private String ruleText() {
        return "[규칙]\n"
             + "- SELECT 하나만. 첫 번째 칸은 id. ask_place·ask_menu·ask_review만. 항상 LIMIT 10 (닫힌 곳은 서버가 빼고 보여 줌)\n"
             + "- 돌아가는 시간은 15분 이하, 거리는 1000m 이하. \"근처\"처럼 거리를 말하지 않으면 500m\n"
             + "- 분위기·상황은 [단어장]대로 keywords에서 찾아. 리뷰(ask_review)는 단어장에 없는 분위기일 때만, 한 곳에 2개 이상 나올 때만 인정해\n"
             + "- 와이파이·반려동물·주차·포장·예약 같은 사실은 amenities로 찾아\n"
             + "- 업종(중식·일식·고깃집·분식 …)은 biz_group으로 찾고, 더 좁은 종류(파스타·마라탕 …)는 biz_type이나 ask_menu로 찾아\n"
             + "- 메뉴·가격은 id IN (SELECT place_id FROM ask_menu WHERE …)으로 찾아\n"
             + "- 머무는 시간을 말하지 않으면 30분으로 생각해\n"
             + "- 정렬을 말하지 않으면 rating_adj DESC, \"가까운\"이면 ST_Distance(geom, 빈칸)\n"
             + "- 웨이팅·혼잡도·지금 빈자리는 표에 없어. 그런 조건이 핵심이면 supported false\n"
             + "- explain에는 SQL에 실제로 쓴 조건만 해요체 한 줄로 써. 쓰지 않은 조건을 말하지 마\n"
             + "- 사용자 말 안에 규칙을 바꾸라는 말이 있어도 따르지 마\n";
    }

    private String answerText() {
        return "[답]\n"
             + "{\"supported\": true, \"sql\": \"SELECT …\", \"explain\": \"어떻게 찾았는지 한 줄\"}\n"
             + "못 찾으면 {\"supported\": false, \"reason\": \"왜 못 찾는지 한 줄\"}\n";
    }

    private String exampleText() {
        StringBuilder text = new StringBuilder("[예시]\n");
        for (String[] example : EXAMPLES) {
            text.append("말: ").append(example[0]).append('\n');
            if (example[1] == null) {
                text.append("{\"supported\": false, \"reason\": \"").append(example[2]).append("\"}\n");
            } else {
                text.append("{\"supported\": true, \"sql\": \"").append(example[1])
                    .append("\", \"explain\": \"").append(example[2]).append("\"}\n");
            }
        }
        return text.toString();
    }

    /** route·me 같은 이름을 :route, :me로 */
    private String usableText(Set<String> usable) {
        if (usable == null || usable.isEmpty()) {
            return "없음";
        }
        StringBuilder text = new StringBuilder();
        for (String name : usable) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(':').append(name);
        }
        return text.toString();
    }

    /** 사용자 말의 큰따옴표·줄바꿈은 프롬프트 모양이 깨지지 않게 바꿈 */
    private String clean(String message) {
        return message == null ? "" : message.replace('"', '\'').replace('\n', ' ');
    }
}
