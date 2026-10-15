package com.tripbiseo.service;
 
import static com.tripbiseo.common.ApiException.fail;
 
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
 
import org.springframework.stereotype.Service;
 
import com.tripbiseo.common.ApiException;
import com.tripbiseo.common.Http;
import com.tripbiseo.common.Json;
import com.tripbiseo.common.Maps;
 
/**
 * 제미나이 호출 한곳. 원칙: 제미나이는 사람 말을 이해하고 고르고 설명만, 시간·거리 계산은 서버가 합니다.
 *  - 키: GEMINI_API_KEY, 모델: GEMINI_MODEL (API 키 관리 화면 → application.properties 순)
 *  - 도구(함수 호출): 날씨처럼 서버가 가진 기능을 제미나이가 필요할 때 부르게 함
 */
@Service
public class GeminiService {
 
    private static final String BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final String DEFAULT_MODEL = "gemini-3.8-flash";
    /** 기본 모델이 붐빌 때 차례로 시도하는 예비 모델 (구글이 최신 flash로 연결해 주는 별칭) */
    private static final List<String> FALLBACK_MODELS = List.of("gemini-flash-latest", "gemini-flash-lite-latest");
    private static final Set<Integer> RETRY_STATUS = Set.of(429, 500, 503, 504);
    private static final int FALLBACK_TIMEOUT_MS = 25000;
 
    private final ApiKeyService keys;
 
    public GeminiService(ApiKeyService keys) {
        this.keys = keys;
    }
 
    public boolean ready() {
        return keys.has("GEMINI_API_KEY");
    }
 
    /** /api/chat (팀 테스트용) 같은 짧은 질문 → 글 */
    public String askText(String prompt) {
        return textOf(generate(body(List.of(user(prompt)), null, false), 30000));
    }
 
    /** 응답을 JSON으로 받기. 형식이 깨져 오면 502 */
    public Map<String, Object> askJson(String prompt) {
        return parseJson(textOf(generate(body(List.of(user(prompt)), null, true), 40000)));
    }
 
    /**
     * 도구를 쓸 수 있게 묻기. 제미나이가 functionCall을 보내면 runTool로 실행해 결과를 돌려주고 다시 묻습니다(최대 3번).
     * 마지막 답은 JSON으로 읽습니다.
     */
    public Map<String, Object> askJsonWithTools(String prompt, List<Map<String, Object>> functions,
                                                BiFunction<String, Map<String, Object>, Object> runTool) {
        List<Object> contents = new ArrayList<>(List.of(user(prompt)));
        List<Object> tools = List.of(Maps.of("functionDeclarations", functions));
        Map<String, Object> cand = generate(body(contents, tools, false), 40000);
        for (int round = 0; round < 3; round++) {
            Map<String, Object> call = null;
            for (Object p : Json.list(cand, "content", "parts")) if (Json.map(p, "functionCall") != null) call = Json.map(p, "functionCall");
            if (call == null) break;
            String name = String.valueOf(call.get("name"));
            Object result = runTool.apply(name, Json.map(call, "args") == null ? Map.of() : Json.map(call, "args"));
            contents.add(cand.get("content")); // 모델이 보낸 그대로 돌려줘야 다음 답이 이어짐
            Map<String, Object> response = Maps.of("name", name, "response", Maps.of("result", result));
            if (call.get("id") != null) response.put("id", call.get("id"));
            contents.add(Maps.of("role", "user", "parts", List.of(Maps.of("functionResponse", response))));
            cand = generate(body(contents, tools, false), 40000);
        }
        return parseJson(textOf(cand));
    }
 
    /** 제미나이가 구글 검색으로 직접 찾아 요약 (날씨 API를 못 부를 때) */
    public String askWithSearch(String prompt) {
        Map<String, Object> b = body(List.of(user(prompt)), List.of(Maps.of("google_search", Map.of())), false);
        return textOf(generate(b, 40000)).trim();
    }
 
    // ---------------------------------------------------------------- 내부
    /**
     * 503(사용량 폭주)·429·500·504는 잠깐 뒤 같은 모델로 한 번 더, 그래도 안 되면 예비 모델로 넘어갑니다.
     * 시간 초과는 같은 모델을 다시 기다리지 않고 바로 예비 모델로 갑니다.
     * 키 오류·요청 형식 오류(4xx)는 다시 해도 같으니 바로 알려 줍니다.
     */
    private Map<String, Object> generate(Map<String, Object> body, int timeoutMs) {
        String key = keys.get("GEMINI_API_KEY");
        if (key == null) throw fail(503, "Gemini 키가 없어요. 마이 → v1.0 → API 키 관리에서 넣어 주세요.");
        String model = keys.get("GEMINI_MODEL") == null ? DEFAULT_MODEL : keys.get("GEMINI_MODEL");
        // 시도 순서: 기본 모델 → (1.5초 뒤) 기본 모델 → 예비 모델들
        Set<String> fallbacks = new LinkedHashSet<>(FALLBACK_MODELS);
        fallbacks.remove(model);
        List<String> attempts = new ArrayList<>(List.of(model, model));
        attempts.addAll(fallbacks);
 
        Http.Result r = null;
        boolean timedOut = false;
        for (int i = 0; i < attempts.size(); i++) {
            if (i == 1 && timedOut) continue; // 느린 모델을 또 기다리지 않고 바로 예비 모델로
            if (i == 1) pause(1500);
            try {
                r = Http.send("POST", BASE + Http.encPath(attempts.get(i)) + ":generateContent", Map.of("x-goog-api-key", key), body,
                        i == 0 ? timeoutMs : FALLBACK_TIMEOUT_MS, "Gemini");
            } catch (ApiException e) {
                if (e.getStatus() != 504) throw e;
                timedOut = true;
                r = null;
                continue;
            }
            if (!RETRY_STATUS.contains(r.status())) break;
        }
        if (r == null) throw fail(504, "Gemini가 응답하지 않아요(시간 초과). 1~2분 뒤 다시 시도해 주세요.");
        if (r.status() < 200 || r.status() >= 300) {
            if (RETRY_STATUS.contains(r.status())) throw fail(503, "Gemini 사용량이 몰려 잠시 응답하지 않아요. 1~2분 뒤 다시 시도해 주세요.");
            String msg = Json.str(r.json(), "error", "message");
            throw fail(502, "Gemini 오류 " + r.status() + (msg == null ? "" : ": " + msg));
        }
        Map<String, Object> cand = Json.map(r.json(), "candidates", 0);
        if (cand == null || cand.get("content") == null) throw fail(502, "Gemini 응답이 비어 있어요.");
        return cand;
    }
 
    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
 
    private static Map<String, Object> body(List<?> contents, List<?> tools, boolean json) {
        Map<String, Object> config = Maps.of("temperature", 0.3);
        if (json) config.put("responseMimeType", "application/json");
        Map<String, Object> b = Maps.of("contents", contents, "generationConfig", config);
        if (tools != null) b.put("tools", tools);
        return b;
    }
 
    private static Map<String, Object> user(String text) {
        return Maps.of("role", "user", "parts", List.of(Maps.of("text", text)));
    }
 
    private static String textOf(Map<String, Object> cand) {
        StringBuilder sb = new StringBuilder();
        for (Object p : Json.list(cand, "content", "parts")) if (Json.str(p, "text") != null) sb.append(Json.str(p, "text"));
        return sb.toString();
    }
 
    /** ```json 감싸기나 앞뒤 말이 붙어 와도 { … } 만 읽기 */
    static Map<String, Object> parseJson(String text) {
        String t = text == null ? "" : text.trim().replaceAll("^```(?:json)?\\s*|\\s*```$", "");
        int a = t.indexOf('{'), b = t.lastIndexOf('}');
        try {
            return Json.parseMap(a >= 0 && b > a ? t.substring(a, b + 1) : t);
        } catch (IllegalArgumentException e) {
            throw fail(502, "Gemini 답을 읽지 못했어요. 다시 시도해 주세요.");
        }
    }
}