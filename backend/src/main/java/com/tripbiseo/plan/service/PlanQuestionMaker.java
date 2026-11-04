package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.tripbiseo.plan.dto.AiConditions;
import com.tripbiseo.plan.dto.PlanConditions;

/**
 * ③ 원하는 것 말하기의 질문 순서 (11/04). 다음 질문은 Gemini가 아니라 서버가 정해요:
 * 아직 안 물어봤고, 답도 아직 안 나온 것 중 앞에서부터 → 꼭/가능하면 갈 곳 → 피하고 싶은 것 → 시간이 정해진 일정.
 * 화면의 첫 말풍선이 첫 질문(visit)이라, 물어본 기록이 없으면 visit은 이미 물어본 걸로 봐요.
 */
@Component
public class PlanQuestionMaker {

    public static final String VISIT = "visit";
    public static final String AVOID = "avoid";
    public static final String TIME = "time";
    private static final List<String> ORDER = List.of(VISIT, AVOID, TIME);
    // 화면 PlanChatView의 첫 말풍선과 같은 글 (visit)
    private static final Map<String, String> TEXTS = Map.of(
            VISIT, "꼭 가고 싶은 곳이나, 가능하면 가고 싶은 곳이 있어요? 예: \"경복궁은 꼭 가고 싶어\", \"시간 되면 한강도 가고 싶어\"",
            AVOID, "피하고 싶은 곳이나 싫은 게 있어요? 예: \"카페는 별로 안 가고 싶어\", \"사람 많은 곳은 싫어\", \"오래 걷는 건 힘들어\"",
            TIME, "시간이 정해진 일정이나 돌아가야 하는 시간이 있어요? 예: \"저녁 7시 공연 예매했어\", \"밤 9시 기차 타야 해\"");
    private static final String DONE_TEXT = "더 말할 게 없으면 아래 '이대로 일정 만들기'를 눌러 주세요.";
    private static final String NOTHING_TEXT = "여행 조건으로 담을 말을 찾지 못했어요. 가고 싶은 곳, 피하고 싶은 것, 시간이 정해진 일정처럼 말해 주세요.";

    /** 물어본 기록 (없으면 첫 말풍선의 visit만) */
    public List<String> askedOf(PlanConditions conditions) {
        boolean isEmpty = conditions.getAsked() == null || conditions.getAsked().isEmpty();
        return isEmpty ? new ArrayList<>(List.of(VISIT)) : new ArrayList<>(conditions.getAsked());
    }

    /** 다음 질문 키: 안 물어봤고 답도 안 나온 것 중 첫 번째. 다 끝났으면 null */
    public String pickNext(PlanConditions conditions, List<String> asked) {
        for (String key : ORDER) {
            if (!asked.contains(key) && !isAnswered(conditions, key)) {
                return key;
            }
        }
        return null;
    }

    /** 조건이 없는 말을 했을 때 다시 물을 질문: 방금 물은 질문의 답이 아직 없으면 그 질문, 있으면 다음 질문 */
    public String pickRepeat(PlanConditions conditions, List<String> asked) {
        String last = asked.get(asked.size() - 1);
        return isAnswered(conditions, last) ? pickNext(conditions, asked) : last;
    }

    /** 질문 글 (다 끝났으면 일정 만들기 안내) */
    public String textOf(String key) {
        return key == null ? DONE_TEXT : TEXTS.get(key);
    }

    /** 조건으로 담을 말이 없었을 때 답 (Gemini 답 대신 늘 같은 글) */
    public String nothingText() {
        return NOTHING_TEXT;
    }

    /** Gemini가 이번 말에서 조건을 하나라도 뽑았는지 (답 문장·제목·스타일은 조건으로 안 침) */
    public boolean hasAnyCondition(AiConditions answer) {
        if (answer == null) {
            return false;
        }
        boolean hasValue = hasText(answer.getRegion()) || hasText(answer.getStartDate()) || hasText(answer.getEndDate())
                || answer.getPeople() != null || answer.getFinish() != null || answer.getMaxWalkMin() != null;
        boolean hasList = isFilled(answer.getTransports()) || isFilled(answer.getMustVisit()) || isFilled(answer.getWishVisit())
                || isFilled(answer.getAvoidVisit())
                || isFilled(answer.getFixed()) || isFilled(answer.getKinds()) || isFilled(answer.getTags())
                || isFilled(answer.getNeeds()) || isFilled(answer.getMustKinds()) || isFilled(answer.getAvoidKinds());
        return hasValue || hasList;
    }

    /** 그 질문의 답이 이미 조건에 있는지 (여행 정보 화면에서 먼저 넣었을 수도 있어서) */
    private boolean isAnswered(PlanConditions conditions, String key) {
        if (key.equals(VISIT)) {
            return isFilled(conditions.getMustVisit()) || isFilled(conditions.getWishVisit()) || isFilled(conditions.getMustKinds());
        }
        if (key.equals(AVOID)) {
            // 걷는 시간·요청만 말했으면 피하고 싶은 곳은 아직 안 물어본 걸로 봄 (피할 곳·종류를 말했을 때만 건너뜀)
            return isFilled(conditions.getAvoidKinds()) || isFilled(conditions.getAvoidVisit());
        }
        return isFilled(conditions.getFixed()) || conditions.getFinish() != null;
    }

    private boolean isFilled(List<?> list) { // List<?>: 안에 든 게 무엇이든 받는 목록
        return list != null && !list.isEmpty();
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}
