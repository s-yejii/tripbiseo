package com.tripbiseo.common.ai;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.common.GoogleGenAiThinkingLevel;
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
    // 11/03: 응답이 없으면 끝없이 기다리던 문제 → 한 번 묻는 데 최대 25초 (사진 읽기도 보통 10초 안)
    private static final long CALL_TIMEOUT_SECONDS = 25;

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
        return ask(prompt, null, false, null, null, false);
    }

    /** 질문 → JSON 답을 type에 담아 줌 */
    @Override
    public <T> T askJson(String prompt, Class<T> type) {
        return readJson(ask(prompt, null, true, null, null, false), type);
    }

    /** 질문 → JSON 답, 생각 단계 LOW로 빨리 (쉬운 분류용) */
    @Override
    public <T> T askJsonQuick(String prompt, Class<T> type) {
        return readJson(ask(prompt, null, true, null, null, true), type);
    }

    /** 사진 + 질문 → JSON 답 (예약 캡처·영수증 읽기). 사진은 저장하지 않고 이번 질문에만 씀 */
    @Override
    public <T> T askJsonWithImage(String prompt, byte[] image, String imageType, Class<T> type) {
        return readJson(ask(prompt, null, true, image, imageType, false), type);
    }

    /** 날씨 도구를 쓸 수 있게 묻기 (좌표는 도구 안에서만 씀) */
    @Override
    public <T> T askJsonWithWeather(String prompt, double lat, double lng, Class<T> type) {
        WeatherTool tool = new WeatherTool(weatherClient, lat, lng);
        String jsonRule = "\n답은 JSON만 보내. 다른 말이나 ```는 붙이지 마.";
        return readJson(ask(prompt + jsonRule, tool, false, null, null, false), type);
    }

    // 모델을 차례로 바꿔 가며 묻기: 기본 모델 → (1.5초 뒤) 기본 모델 → 예비 모델들. isQuick: 생각 단계 LOW (11/04)
    private String ask(String prompt, Object tool, boolean isJson, byte[] image, String imageType, boolean isQuick) {
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
                return callOnce(prompt, tool, isJson, attempts.get(i), image, imageType, isQuick);
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
    private String callOnce(String prompt, Object tool, boolean isJson, String modelName, byte[] image, String imageType,
                            boolean isQuick) {
        GoogleGenAiChatOptions.Builder options = GoogleGenAiChatOptions.builder().model(modelName);
        if (isJson) {
            options.responseMimeType("application/json");
        }
        if (isQuick) {
            // 생각 단계(thinking level): 3.x Flash 기본은 MEDIUM. 분류는 LOW로 충분하고 더 빠름
            // (MINIMAL은 gemini-3.8·3.7-flash가 400 오류를 내서 안 씀)
            options.thinkingLevel(GoogleGenAiThinkingLevel.LOW);
        }
        ChatClient.ChatClientRequestSpec request;
        if (image == null) {
            request = chatClient.prompt().user(prompt).options(options);
        } else {
            // media: 질문에 사진을 같이 붙임 (ByteArrayResource: 메모리에 있는 바이트를 파일처럼 넘기는 도구)
            ByteArrayResource resource = new ByteArrayResource(image);
            request = chatClient.prompt().user(user -> user.text(prompt).media(MimeTypeUtils.parseMimeType(imageType), resource)).options(options);
        }
        if (tool != null) {
            request = request.tools(tool);
        }
        String answer = callWithTimeout(request);
        if (answer == null || answer.isBlank()) {
            throw fail(502, EMPTY);
        }
        return answer;
    }

    // 정해진 시간 안에 답이 없으면 기다리지 않고 504 (시간 초과)
    // CompletableFuture: 호출을 다른 일꾼(스레드)에게 맡기고, 여기서는 정해진 시간만큼만 기다리는 도구
    private String callWithTimeout(ChatClient.ChatClientRequestSpec request) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> request.call().content());
        try {
            return future.get(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw fail(504, TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail(504, TIMEOUT);
        } catch (ExecutionException e) {
            // 일꾼 안에서 난 오류를 그대로 다시 던져서 toApiException이 원래 오류로 바꾸게 함
            throw e.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(e.getCause());
        }
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
    // 사용량 몰림(503)만 다른 모델로 다시. 시간 초과(504)는 다시 해도 또 기다리게 돼서 바로 실패로 (11/03)
    private static boolean isRetryable(ApiException e) {
        return e.getStatus() == 503;
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
