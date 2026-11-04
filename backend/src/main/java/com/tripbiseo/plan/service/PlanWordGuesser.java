package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tripbiseo.plan.dto.AiConditions;

/**
 * ③ Gemini가 안 될 때(시간 초과·키 없음) 대화 말을 낱말로만 조건으로 바꿈 (11/04).
 * Gemini보다 훨씬 좁아요: 장소 종류 6개 넣기·빼기 + "○○ 가고 싶어" 꼴의 장소 이름만 읽고, 시각·걷는 시간·리뷰 요청은 못 읽음.
 * "카페 가고 싶어" → kinds [cafe], "카페는 별로 안 가고 싶어" → avoidKinds [cafe] 정도만. 장소 이름·시각은 못 읽음.
 * 문장을 쉼표·마침표·"그리고"로 나눠, 그 조각에 부정 낱말(별로·싫·빼·말고·안 가…)이 있으면 피하기로 봄
 */
@Component
public class PlanWordGuesser {

    // 종류 코드 → 그 종류를 말할 때 흔히 쓰는 낱말 ("술"만 쓰면 "미술관"에 걸려서 "술집"·"술은"처럼 씀)
    private static final Map<String, List<String>> KIND_WORDS = new LinkedHashMap<>();
    static {
        KIND_WORDS.put("cafe", List.of("카페", "커피", "디저트", "빵집"));
        KIND_WORDS.put("food", List.of("맛집", "밥집", "식당", "밥 먹"));
        KIND_WORDS.put("bar", List.of("술집", "술은", "술 마", "맥주", "와인", "이자카야", "포차"));
        KIND_WORDS.put("shop", List.of("쇼핑", "기념품", "선물"));
        KIND_WORDS.put("culture", List.of("전시", "미술관", "박물관"));
        KIND_WORDS.put("tour", List.of("산책", "관광", "공원", "구경"));
    }
    private static final List<String> NO_WORDS = List.of("별로", "싫", "빼", "말고", "안 가", "안가", "관심 없", "관심없", "안 마", "안마", "못 ", "않");
    // "한강 가고 싶어", "경복궁은 꼭 가고 싶어", "롯데타워 갈래" → 앞의 이름(2~15자)을 꺼냄. 이름 뒤 조사(은·는·도…)는 빼고
    private static final Pattern PLACE_PATTERN = Pattern.compile(
            "([가-힣A-Za-z0-9 ]{2,15}?)\\s*(?:은|는|도|이|가|을|를|에|엔)?\\s*(?:꼭|무조건|반드시)?\\s*(?:가\\s*보고\\s*싶|가고\\s*싶|갈래|가야)");
    // 이름 앞에 붙는 말 ("시간 되면 한강" → "한강")
    private static final String LEAD_WORDS = "^(?:시간\\s*되면|시간\\s*남으면|여유\\s*되면|가능하면|되면|나는|저는|우리는|그리고)\\s*";
    private static final List<String> MUST_WORDS = List.of("꼭", "무조건", "반드시", "가야");
    private static final int MIN_NAME_LENGTH = 2;
    // 말 조각 나누기: 쉼표·마침표, "그리고·근데·지만·는데", "~고 "("별로고 미술관은" → "별로" / "미술관은")
    // (?<=[가-힣])고\\s(?!싶): 한글 바로 뒤의 "고 " (앞 글자는 남기고 "고 "에서만 자름). "가고 싶어"의 "고 "는 안 자름
    private static final String CLAUSE_SPLIT = "[,.!?\\n]|그리고|근데|그런데|지만|는데|(?<=[가-힣])고\\s(?!싶)";
    private static final Map<String, String> KIND_NAMES = Map.of("cafe", "카페", "food", "밥집", "bar", "술집", "shop", "쇼핑",
            "culture", "전시·문화", "tour", "관광·산책");

    /** 말 → 종류 조건만. 못 찾으면 kinds·avoidKinds가 빈 답 */
    public AiConditions guess(String message) {
        List<String> kinds = new ArrayList<>();
        List<String> avoidKinds = new ArrayList<>();
        List<String> mustVisit = new ArrayList<>();
        List<String> wishVisit = new ArrayList<>();
        for (String clause : message.split(CLAUSE_SPLIT)) {
            boolean isNo = containsAny(clause, NO_WORDS);
            String place = isNo ? null : findPlaceName(clause);
            if (place != null) {
                // 꼭·가야면 꼭 갈 곳, 아니면 가능하면 갈 곳 (Gemini 규칙과 같게 애매하면 가능하면)
                if (containsAny(clause, MUST_WORDS)) {
                    mustVisit.add(place);
                } else {
                    wishVisit.add(place);
                }
            }
            for (Map.Entry<String, List<String>> entry : KIND_WORDS.entrySet()) { // Map.Entry: 표의 한 줄(키 + 값)
                if (!containsAny(clause, entry.getValue())) {
                    continue;
                }
                if (isNo) {
                    avoidKinds.add(entry.getKey());
                } else {
                    kinds.add(entry.getKey());
                }
            }
        }
        AiConditions answer = new AiConditions();
        answer.setReply(makeReply(kinds, avoidKinds, mustVisit, wishVisit));
        answer.setKinds(kinds);
        answer.setAvoidKinds(avoidKinds);
        answer.setMustVisit(mustVisit);
        answer.setWishVisit(wishVisit);
        return answer;
    }

    /** "○○ 가고 싶어" 꼴이면 ○○ (앞말·종류 낱말은 빼고). 아니면 null */
    private String findPlaceName(String clause) {
        Matcher matcher = PLACE_PATTERN.matcher(clause.trim());
        if (!matcher.find()) {
            return null;
        }
        String name = matcher.group(1).trim().replaceFirst(LEAD_WORDS, "").trim();
        for (List<String> words : KIND_WORDS.values()) {
            if (words.contains(name)) {
                return null; // "카페 가고 싶어"의 카페는 장소 이름이 아니라 종류
            }
        }
        return name.length() < MIN_NAME_LENGTH ? null : name;
    }

    /**
     * 사용자에게 보일 답: 읽은 것만 그대로 ("카페 넣고, 술집 뺄게요."). 기술 얘기(AI 연결 등)는 안 씀.
     * 낱말로 못 읽은 말(장소 이름·시각)은 일정 만들 때 한 말 그대로 AI에게 넘어가서 "같이 볼게요"라고 함
     */
    private String makeReply(List<String> kinds, List<String> avoidKinds, List<String> mustVisit, List<String> wishVisit) {
        List<String> adds = new ArrayList<>();
        if (!mustVisit.isEmpty()) {
            adds.add(String.join(", ", mustVisit) + " 꼭");
        }
        if (!wishVisit.isEmpty()) {
            adds.add(String.join(", ", wishVisit) + " 가능하면");
        }
        if (!kinds.isEmpty()) {
            adds.add(toNames(kinds));
        }
        String add = adds.isEmpty() ? "" : String.join(", ", adds) + (avoidKinds.isEmpty() ? " 넣을게요." : " 넣고, ");
        String remove = avoidKinds.isEmpty() ? "" : toNames(avoidKinds) + " 뺄게요.";
        return add + remove + " 다른 말은 일정을 만들 때 같이 볼게요.";
    }

    private String toNames(List<String> codes) {
        List<String> names = new ArrayList<>();
        for (String code : codes) {
            names.add(KIND_NAMES.get(code));
        }
        return String.join(", ", names);
    }

    private boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
