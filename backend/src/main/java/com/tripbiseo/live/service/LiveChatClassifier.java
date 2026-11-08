package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.live.dto.ChatIntent;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 채팅 말 분류: 추천(recommend) / 일정 바꾸기(change) / 질문(question).
 * 규칙 먼저 → 한 종류만 걸리면 확실해서 바로 결과 → 애매할 때만 Gemini(JSON, 고를 값 고정) → 서버가 값 확인 → 그래도 모르면 질문.
 * 근거: 자주 하는 말은 정해져 있어 규칙이 빠르고 늘 같은 결과. Gemini가 꺼져도 채팅이 돌아감. 좌표는 프롬프트에 넣지 않음.
 */
@Component
@RequiredArgsConstructor
public class LiveChatClassifier {

    private static final String[] RECOMMEND_WORDS = {"추천", "근처", "어디 가", "갈 만한", "갈만한", "먹을", "마실", "배고",
                                                     "가는 길", "들를", "들르", "다녀올"}; // 뒤 4개: AI에게 묻기 (11/08)
    private static final String[] CHANGE_WORDS = {"바꿔", "바꾸", "늦", "빼", "다시 짜", "일정 변경"};
    private static final String[] QUESTION_WORDS = {"몇 시", "몇시", "다음", "언제", "여유", "남았", "날씨", "비 와", "비와", "출발"};
    private static final String[] CATEGORIES = {"카페", "디저트", "식당", "술집"};
    private static final String DEFAULT_CATEGORY = "카페";

    private final AiService aiService;

    /** 말 분류 */
    public ChatIntent classify(String message) {
        ChatIntent byRule = classifyByRule(message);
        if (byRule != null) {
            return byRule;
        }
        ChatIntent byAi = classifyByAi(message);
        return byAi != null ? byAi : makeIntent("question", message);
    }

    // ---------------------------------------------------------------- 규칙

    /** 낱말 규칙: 한 종류만 걸리면 그 종류, 아무것도 안 걸리거나 여러 종류면 null(애매) */
    private ChatIntent classifyByRule(String message) {
        // 장소 종류 + 추천 말이 같이 있으면 "다음·여유"가 섞여도 추천 ("다음 장소 근처 카페", 11/08)
        boolean isClearRecommend = findCategory(message) != null && containsAny(message, RECOMMEND_WORDS);
        if (isClearRecommend && !containsAny(message, CHANGE_WORDS)) {
            return makeIntent("recommend", message);
        }
        boolean isRecommend = containsAny(message, RECOMMEND_WORDS) || findCategory(message) != null;
        boolean isChange = containsAny(message, CHANGE_WORDS);
        boolean isQuestion = containsAny(message, QUESTION_WORDS);
        int matchCount = (isRecommend ? 1 : 0) + (isChange ? 1 : 0) + (isQuestion ? 1 : 0);
        if (matchCount != 1) {
            return null;
        }
        if (isRecommend) {
            return makeIntent("recommend", message);
        }
        return makeIntent(isChange ? "change" : "question", message);
    }

    /** 종류에 맞게 category·tags까지 채운 결과 */
    private ChatIntent makeIntent(String kind, String message) {
        ChatIntent intent = new ChatIntent();
        intent.setKind(kind);
        if ("recommend".equals(kind)) {
            String category = findCategory(message);
            intent.setCategory(category == null ? DEFAULT_CATEGORY : category);
            intent.setTags(findTags(message));
        }
        return intent;
    }

    /** 말에 들어간 장소 종류 (밥·맛집은 식당, 술·맥주는 술집) */
    private String findCategory(String message) {
        if (containsAny(message, new String[] {"카페", "커피"})) {
            return "카페";
        }
        if (containsAny(message, new String[] {"디저트", "빵", "케이크"})) {
            return "디저트";
        }
        if (containsAny(message, new String[] {"식당", "맛집", "밥", "점심", "저녁"})) {
            return "식당";
        }
        if (containsAny(message, new String[] {"술집", "술", "맥주", "와인"})) {
            return "술집";
        }
        return null;
    }

    /** 말에 들어간 분위기 태그 (PlaceTagReader의 태그 이름과 같음) */
    private List<String> findTags(String message) {
        List<String> tags = new ArrayList<>();
        addTagIf(tags, message, "혼밥", new String[] {"혼밥", "혼자"});
        addTagIf(tags, message, "조용", new String[] {"조용", "한적", "작업", "공부"});
        addTagIf(tags, message, "분위기", new String[] {"분위기", "감성", "예쁜"});
        addTagIf(tags, message, "데이트", new String[] {"데이트", "연인"});
        addTagIf(tags, message, "가성비", new String[] {"가성비", "싼", "저렴"});
        addTagIf(tags, message, "웨이팅없음", new String[] {"웨이팅 없", "줄 안", "기다리기 싫", "바로 먹"});
        return tags;
    }

    private void addTagIf(List<String> tags, String message, String tag, String[] words) {
        if (containsAny(message, words)) {
            tags.add(tag);
        }
    }

    private boolean containsAny(String message, String[] words) {
        for (String word : words) {
            if (message.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- Gemini (애매할 때만)

    /** Gemini 분류. 키가 없거나 실패하거나 값이 이상하면 null */
    private ChatIntent classifyByAi(String message) {
        if (!aiService.isReady()) {
            return null;
        }
        try {
            ChatIntent intent = aiService.askJson(makePrompt(message), ChatIntent.class);
            return isValid(intent) ? intent : null;
        } catch (ApiException e) {
            return null;
        }
    }

    /** 고를 값을 정해 주고 JSON으로만 답하게 함 (좌표·회원 정보 없음) */
    private String makePrompt(String message) {
        return "여행 중인 사용자의 말을 분류해. JSON으로만 답해.\n"
             + "kind: \"recommend\"(근처 장소를 찾아 달라) | \"change\"(일정을 바꾸고 싶다·늦었다) | \"question\"(그 밖의 질문) 중 하나\n"
             + "category: \"카페\" | \"디저트\" | \"식당\" | \"술집\" 중 하나 (recommend일 때만, 모르면 \"카페\")\n"
             + "tags: [\"혼밥\",\"조용\",\"분위기\",\"데이트\",\"가성비\",\"웨이팅없음\"] 중 해당하는 것만 (없으면 [])\n"
             + "사용자 말: \"" + message + "\"";
    }

    /** 서버 재확인: kind가 셋 중 하나, category·tags는 정한 값만 남김 */
    private boolean isValid(ChatIntent intent) {
        if (intent == null || intent.getKind() == null) {
            return false;
        }
        String kind = intent.getKind();
        if (!kind.equals("recommend") && !kind.equals("change") && !kind.equals("question")) {
            return false;
        }
        if (kind.equals("recommend") && !isKnownCategory(intent.getCategory())) {
            intent.setCategory(DEFAULT_CATEGORY);
        }
        intent.setTags(keepKnownTags(intent.getTags()));
        return true;
    }

    private boolean isKnownCategory(String category) {
        for (String known : CATEGORIES) {
            if (known.equals(category)) {
                return true;
            }
        }
        return false;
    }

    private List<String> keepKnownTags(List<String> tags) {
        List<String> known = List.of("혼밥", "조용", "분위기", "데이트", "가성비", "웨이팅없음");
        List<String> kept = new ArrayList<>();
        if (tags == null) {
            return kept;
        }
        for (String tag : tags) {
            if (known.contains(tag)) {
                kept.add(tag);
            }
        }
        return kept;
    }
}
