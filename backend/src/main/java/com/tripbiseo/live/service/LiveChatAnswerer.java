package com.tripbiseo.live.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.live.dto.StatusStop;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 질문 답 (RAG: 서버가 계산한 오늘 상황을 근거로 넣고 그 안에서만 답하게 함).
 *  R 찾기: StatusService가 계산한 지금 상황 (문서 검색 대신 결정적인 계산)
 *  A 넣기: 이름·시각·분만 짧은 글로 (좌표는 넣지 않음, 10/30 결정)
 *  G 만들기: "상황에 없는 건 모르겠어요라고 해", "숫자는 적힌 것만"
 *  검사: 답에 나온 시각·분이 근거에 있는지 코드로 확인 → 없으면 지어낸 것으로 보고 기본 문장
 * 자주 하는 질문(다음 일정·여유·비)은 Gemini 없이 템플릿으로 바로 답해요.
 */
@Component
@RequiredArgsConstructor
public class LiveChatAnswerer {

    private static final String FALLBACK = "지금은 답하기 어려워요. 일정 화면을 확인해 주세요.";
    private static final int MAX_REPLY_LENGTH = 200;
    // Pattern: 글자에서 "14:25", "20분" 같은 모양을 찾는 정규식 (java.util.regex)
    private static final Pattern TIME = Pattern.compile("\\d{1,2}:\\d{2}");
    private static final Pattern MINUTES = Pattern.compile("\\d+분");

    private final AiService aiService;

    /** 오늘 상황을 근거로 질문에 답 */
    public String answer(String message, StatusResponse status) {
        String byTemplate = answerByTemplate(message, status);
        if (byTemplate != null) {
            return byTemplate;
        }
        if (!aiService.isReady()) {
            return FALLBACK;
        }
        String context = makeContext(status);
        try {
            String reply = aiService.askText(makePrompt(context, message));
            if (isGrounded(reply, context)) {
                return reply.trim();
            }
        } catch (ApiException e) {
            // Gemini 실패 → 기본 문장
        }
        return FALLBACK;
    }

    // ---------------------------------------------------------------- 템플릿 (Gemini 없이)

    /** 자주 하는 질문은 정해진 문장. 해당 없으면 null */
    private String answerByTemplate(String message, StatusResponse status) {
        if (message.contains("다음") || message.contains("어디 가")) {
            StatusStop next = status.getNext() == null ? null : findStop(status, status.getNext().getId());
            return next == null ? "오늘 남은 일정이 없어요." : "다음은 " + next.getName() + "이에요. " + next.getEta() + "에 도착할 예정이에요.";
        }
        if (message.contains("여유") || message.contains("늦")) {
            return answerSlack(status);
        }
        if (message.contains("날씨") || message.contains("비 와") || message.contains("비와") || message.contains("우산")) {
            return status.getWeather() == null ? "오늘은 비 소식이 없어요." : status.getWeather().getTime() + "부터 비 소식이 있어요.";
        }
        return null;
    }

    /** 고정 일정 여유 / 지각 */
    private String answerSlack(StatusResponse status) {
        if (status.getFixed() == null) {
            return "오늘 남은 고정 일정이 없어요.";
        }
        int slack = status.getFixed().getSlackMin();
        String name = status.getFixed().getName();
        return slack >= 0 ? name + "까지 " + slack + "분 여유가 있어요." : "지금대로면 " + name + "에 " + (-slack) + "분 늦어요.";
    }

    // ---------------------------------------------------------------- RAG

    /** A 넣기: 오늘 상황 → 짧은 글 (이름·시각·분만, 좌표 없음) */
    private String makeContext(StatusResponse status) {
        StringBuilder text = new StringBuilder(); // StringBuilder: 글자를 여러 번 이어 붙일 때 씀
        text.append("지금 ").append(status.getNow()).append("\n");
        StatusStop current = findStop(status, status.getCurrentStopId());
        text.append("지금 있는 곳: ").append(current == null ? "이동 중" : current.getName()).append("\n");
        text.append("남은 일정:\n");
        for (StatusStop stop : status.getStops()) {
            if ("done".equals(stop.getReach()) || "here".equals(stop.getReach())) {
                continue; // 다녀온 칸·지금 칸은 질문에 필요 없음
            }
            text.append("- ").append(stop.getName()).append(" ").append(stop.getEta()).append(" 도착 예정, ").append(stop.getStay()).append("분");
            if (Boolean.TRUE.equals(stop.getFixed())) {
                text.append(" (고정 일정, 계획 ").append(stop.getTime()).append(")");
            }
            text.append("\n");
        }
        if (status.getFixed() != null) {
            text.append(status.getFixed().getName()).append("까지 여유: ").append(status.getFixed().getSlackMin()).append("분\n");
        }
        if (status.getWeather() != null) {
            text.append("비 소식: ").append(status.getWeather().getTime()).append("부터\n");
        }
        return text.toString();
    }

    /** G 만들기: 근거 + 규칙 + 질문 */
    private String makePrompt(String context, String message) {
        return "너는 여행 비서야. 아래 [오늘 상황]만 보고 질문에 한두 문장으로 답해.\n"
             + "상황에 없는 내용은 \"모르겠어요\"라고 해. 시각과 분은 상황에 적힌 숫자만 써.\n\n"
             + "[오늘 상황]\n" + context + "\n[질문]\n" + message;
    }

    /** 검사: 답의 시각("14:25")·분("20분")이 모두 근거에 있나. 하나라도 없으면 지어낸 것으로 봄 */
    private boolean isGrounded(String reply, String context) {
        if (reply == null || reply.isBlank() || reply.length() > MAX_REPLY_LENGTH) {
            return false;
        }
        return isAllInContext(TIME.matcher(reply), context) && isAllInContext(MINUTES.matcher(reply), context);
    }

    private boolean isAllInContext(Matcher matcher, String context) {
        while (matcher.find()) {
            if (!context.contains(matcher.group())) {
                return false;
            }
        }
        return true;
    }

    /** id로 칸 찾기 (없으면 null) */
    private StatusStop findStop(StatusResponse status, Long stopId) {
        if (stopId == null) {
            return null;
        }
        for (StatusStop stop : status.getStops()) {
            if (stopId.equals(stop.getId())) {
                return stop;
            }
        }
        return null;
    }
}
