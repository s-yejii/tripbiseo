package com.tripbiseo.common.ai;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.WeatherClient;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Spring AI로 Gemini를 부릅니다.
 *  - 붐비면(429·500·503·504) 1.5초 뒤 같은 모델로 한 번 더 → 그래도 안 되면 예비 모델로 넘어감
 *  - 키 오류·요청 형식 오류(그 밖의 4xx)는 다시 해도 같으니 바로 알려 줌
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);
    private static final List<String> FALLBACK_MODELS = List.of("gemini-3.6-flash", "gemini-3.5-flash-lite");
    private static final List<Integer> RETRY_STATUS = List.of(429, 500, 503, 504);
    private static final long RETRY_WAIT_MS = 1500;

    private static final String NO_KEY = "Gemini 키가 없어요. 환경변수 GEMINI_API_KEY를 확인해 주세요.";
    private static final String BUSY = "Gemini 사용량이 몰려 잠시 응답하지 않아요. 1~2분 뒤 다시 시도해 주세요.";
    private static final String TIMEOUT = "Gemini가 응답하지 않아요(시간 초과). 1~2분 뒤 다시 시도해 주세요.";
    private static final String EMPTY = "Gemini 응답이 비어 있어요.";
    private static final String BAD_JSON = "Gemini 답을 읽지 못했어요. 다시 시도해 주세요.";

    // ChatClient: Spring AI가 주는 "AI에게 묻기" 도구. prompt().user(질문).call().content()로 답을 받음
    private final ChatClient chatClient;
    private final WeatherClient weatherClient;
    private final ObjectMapper objectMapper;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;
    @Value("${spring.ai.google.genai.chat.model:gemini-3.8-flash}")
    private String model;

    public AiServiceImpl(ChatClient.Builder chatClientBuilder, WeatherClient weatherClient, ObjectMapper objectMapper) {
        this.chatClient = chatClientBuilder.build();
        this.weatherClient = weatherClient;
        this.objectMapper = objectMapper;
    }

    /** Gemini 키가 있는지 */
    @Override
    public boolean isReady() {
        return !apiKey.isBlank();
    }

    /** 질문 → 글 답 */
    @Override
    public String askText(String prompt) {
        return ask(prompt, null, false);
    }

    /** 질문 → JSON 답을 type에 담아 줌 */
    @Override
    public <T> T askJson(String prompt, Class<T> type) {
        return readJson(ask(prompt, null, true), type);
    }

    /** 날씨 도구를 쓸 수 있게 묻기 (좌표는 도구 안에서만 씀) */
    @Override
    public <T> T askJsonWithWeather(String prompt, double lat, double lng, Class<T> type) {
        WeatherTool tool = new WeatherTool(weatherClient, lat, lng);
        String jsonRule = "\n답은 JSON만 보내. 다른 말이나 ```는 붙이지 마.";
        return readJson(ask(prompt + jsonRule, tool, false), type);
    }

    // 모델을 차례로 바꿔 가며 묻기: 기본 모델 → (1.5초 뒤) 기본 모델 → 예비 모델들
    private String ask(String prompt, Object tool, boolean isJson) {
        if (!isReady()) {
            throw fail(503, NO_KEY);
        }
        List<String> attempts = modelOrder();
        ApiException lastError = null;
        for (int i = 0; i < attempts.size(); i++) {
            if (i == 1) {
                pause(RETRY_WAIT_MS);
            }
            try {
                return callOnce(prompt, tool, isJson, attempts.get(i));
            } catch (RuntimeException e) {
                lastError = toApiException(e);
                log.info("Gemini {} 실패 ({}): {}", attempts.get(i), lastError.getStatus(), lastError.getMessage());
                if (!isRetryable(lastError)) {
                    throw lastError;
                }
            }
        }
        throw lastError;
    }

    // 한 모델로 한 번 묻기. 도구를 쓸 때는 Gemini가 JSON 모드와 도구를 같이 못 써서 JSON 모드를 끔
    private String callOnce(String prompt, Object tool, boolean isJson, String modelName) {
        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder().model(modelName);
        if (isJson) {
            options.responseMimeType("application/json");
        }
        ChatClient.ChatClientRequestSpec request = chatClient.prompt().user(prompt).options(options);
        if (tool != null) {
            request = request.tools(tool);
        }
        String answer = request.call().content();
        if (answer == null || answer.isBlank()) {
            throw fail(502, EMPTY);
        }
        return answer;
    }

    private List<String> modelOrder() {
        List<String> order = new ArrayList<>();
        order.add(model);
        order.add(model);
        for (String fallback : FALLBACK_MODELS) {
            if (!fallback.equals(model)) {
                order.add(fallback);
            }
        }
        return order;
    }

    // Spring AI·Google 라이브러리 오류를 API.md 문장으로 바꿈. 원인(cause)을 따라 내려가며 Google 오류를 찾음
    private static ApiException toApiException(RuntimeException e) {
        if (e instanceof ApiException apiException) {
            return apiException;
        }
        Throwable cause = e;
        while (cause != null) {
            if (cause instanceof com.google.genai.errors.ApiException googleError) {
                return fromStatus(googleError.code(), googleError.message());
            }
            if (cause instanceof SocketTimeoutException) {
                return fail(504, TIMEOUT);
            }
            cause = cause.getCause();
        }
        return fail(502, "Gemini 오류: " + e.getMessage());
    }

    private static ApiException fromStatus(int status, String message) {
        if (status == 504) {
            return fail(504, TIMEOUT);
        }
        if (RETRY_STATUS.contains(status)) {
            return fail(503, BUSY);
        }
        return fail(502, "Gemini 오류 " + status + (message == null ? "" : ": " + message));
    }

    // 붐빔(503)·시간 초과(504)만 다시 시도
    private static boolean isRetryable(ApiException e) {
        return e.getStatus() == 503 || e.getStatus() == 504;
    }

    // ```json 감싸기나 앞뒤 말이 붙어 와도 { … } 부분만 읽기
    private <T> T readJson(String text, Class<T> type) {
        String trimmed = text.trim().replaceAll("^```(?:json)?\\s*|\\s*```$", "");
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        String json = start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw fail(502, BAD_JSON);
        }
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
