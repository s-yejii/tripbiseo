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

    /** 예시: {사용자 말, SQL (못 찾는 질문이면 null), 설명 또는 못 찾는 이유} */
    static final List<String[]> EXAMPLES = List.of(
        new String[] {"가는 길에 5분 안에 들를 카페",
            "SELECT id FROM ask_place WHERE kind = 'cafe' AND detour_minutes(geom, :route) <= 5 "
                + "ORDER BY rating_adj DESC LIMIT 5",
            "가는 길에서 5분 안에 들를 수 있는 카페를 평점 순으로 찾았어요"},
        new String[] {"다음 일정 전에 다녀올 수 있는 디저트집",
            "SELECT id FROM ask_place WHERE kind = 'cafe' AND (biz_type ILIKE '%디저트%' OR biz_type ILIKE '%베이커리%') "
                + "AND detour_minutes(geom, :route) + 30 <= :slack ORDER BY rating_adj DESC LIMIT 5",
            "들러서 30분 머물러도 다음 일정에 늦지 않는 디저트집을 찾았어요"},
        new String[] {"근처 와이파이 되는 조용한 카페",
            "SELECT p.id FROM ask_place p WHERE p.kind = 'cafe' AND ST_DWithin(p.geom, :me, 500) "
                + "AND p.amenities ILIKE '%무선 인터넷%' "
                + "AND (SELECT COUNT(*) FROM ask_review r WHERE r.place_id = p.id AND r.content ILIKE '%조용%') >= 2 "
                + "ORDER BY p.rating_adj DESC LIMIT 5",
            "500m 안 와이파이 되는 카페 중 리뷰 2개 이상에 '조용'이 나온 곳이에요"},
        new String[] {"다음 장소 근처 리뷰 많은 고깃집",
            "SELECT id FROM ask_place WHERE kind = 'food' AND biz_type ILIKE '%고기%' AND ST_DWithin(geom, :next, 500) "
                + "ORDER BY review_count DESC LIMIT 5",
            "다음 장소 500m 안 고깃집을 리뷰 많은 순으로 찾았어요"},
        new String[] {"근처 반려동물 데려갈 수 있는 카페",
            "SELECT id FROM ask_place WHERE kind = 'cafe' AND amenities ILIKE '%반려동물 동반%' AND ST_DWithin(geom, :me, 700) "
                + "ORDER BY ST_Distance(geom, :me) LIMIT 5",
            "가게 정보에 반려동물 동반이 있는 700m 안 카페를 가까운 순으로 찾았어요"},
        new String[] {"만원 이하 파스타", null,
            "가격 정보가 없어서 가격으로는 찾을 수 없어요"});

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

    /** 쓸 수 있는 표 (18_ask_place.sql의 뷰) */
    private String tableText() {
        return "[표]\n"
             + "ask_place: 장소\n"
             + "  id, name,\n"
             + "  kind(종류: cafe | food | bar | tour | culture | shop | stay | etc. 디저트·베이커리는 cafe, 술집은 bar),\n"
             + "  biz_type(세부 종류 글 예: 카페,디저트 / 베이커리 / 중식당 / 육류,고기요리 / 분식 / 이자카야 / 일식당),\n"
             + "  rating_adj(보정 평점, 대부분 4.5~4.9), review_count(리뷰 수),\n"
             + "  amenities(가게 정보 편의시설 글: 무선 인터넷, 포장, 예약, 단체 이용 가능, 주차, 유아의자, 반려동물 동반, 노키즈존),\n"
             + "  geom(위치)\n"
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

    private String ruleText() {
        return "[규칙]\n"
             + "- SELECT 하나만. 첫 번째 칸은 id. ask_place·ask_review만. LIMIT 10 이하\n"
             + "- 돌아가는 시간은 15분 이하, 거리는 1000m 이하\n"
             + "- 와이파이·반려동물·주차·포장·예약·단체 같은 사실은 amenities로만 찾고, 리뷰로 찾지 마\n"
             + "- 리뷰(ask_review)는 조용한·뷰·데이트 같은 분위기에만 쓰고, 한 곳에 2개 이상 나올 때만 인정해\n"
             + "- 머무는 시간을 말하지 않으면 30분으로 생각해\n"
             + "- 정렬을 말하지 않으면 rating_adj DESC\n"
             + "- 가격·메뉴·혼잡도·웨이팅 시간은 표에 없어. 그런 조건이 핵심이면 supported false\n";
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
