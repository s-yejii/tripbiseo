package com.tripbiseo.common;
 
import static com.tripbiseo.common.ApiException.fail;
 
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
 
/**
 * 외부 API 호출(자바 기본 HttpClient). 실패는 모두 ApiException으로 바꿔 화면에 이유가 보이게 합니다.
 *   연결 실패 → 502, 시간 초과 → 504 "OO 서버에 연결하지 못했습니다"
 *   401/403             → 502 "OO 키가 거절되었습니다"
 *   그 밖의 오류 상태     → 502 "OO 응답 오류 상태코드"
 *   JSON이 아님(공공데이터 키 오류는 XML로 옴) → 502 + XML 안의 이유
 */
public final class Http {
 
    private Http() {}
 
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final Pattern XML_REASON = Pattern.compile("<(?:returnAuthMsg|resultMsg)>([^<]+)<");
 
    /** 상태 코드 + JSON(읽을 수 있으면) */
    public record Result(int status, Object json, String raw) {}
 
    public static Object getJson(String url, Map<String, String> headers, int timeoutMs, String label) {
        return check(send("GET", url, headers, null, timeoutMs, label), label);
    }
 
    public static Object postJson(String url, Map<String, String> headers, Object body, int timeoutMs, String label) {
        return check(send("POST", url, headers, body, timeoutMs, label), label);
    }
 
    /** 오류 판정은 호출한 쪽이 직접(예: Gemini는 오류 본문의 message를 보여 줌) */
    public static Result send(String method, String url, Map<String, String> headers, Object body, int timeoutMs, String label) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMillis(timeoutMs)).header("Accept", "application/json");
        if (headers != null) headers.forEach(b::header);
        if (body == null) b.method(method, HttpRequest.BodyPublishers.noBody());
        else b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));
        HttpResponse<String> res;
        try {
            res = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw fail(504, label + " 서버에 연결하지 못했습니다. (시간 초과)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fail(502, label + " 호출이 중단되었습니다.");
        } catch (Exception e) {
            throw fail(502, label + " 서버에 연결하지 못했습니다. (네트워크)");
        }
        Object json;
        try {
            json = Json.parse(res.body());
        } catch (IllegalArgumentException e) {
            json = null;
        }
        return new Result(res.statusCode(), json, res.body());
    }
 
    private static Object check(Result r, String label) {
        if (r.status() == 401 || r.status() == 403) throw fail(502, label + " 키가 거절되었습니다. 키와 사용 신청 상태를 확인하세요.");
        if (r.status() < 200 || r.status() >= 300) throw fail(502, label + " 응답 오류 " + r.status());
        if (r.json() == null) {
            Matcher m = XML_REASON.matcher(r.raw() == null ? "" : r.raw());
            throw fail(502, label + " 응답을 읽지 못했습니다." + (m.find() ? " (" + m.group(1) + ")" : ""));
        }
        return r.json();
    }
 
    // ---------------------------------------------------------------- 주소 만들기
    public static String enc(String v) {
        return URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8);
    }
 
    /** 경로 조각(공백은 %20) */
    public static String encPath(String v) {
        return enc(v).replace("+", "%20");
    }
 
    /** base + "?" + k=v&… (null 값은 빼기) */
    public static String url(String base, Map<String, ?> params) {
        StringJoiner q = new StringJoiner("&");
        params.forEach((k, v) -> { if (v != null) q.add(enc(k) + "=" + enc(String.valueOf(v))); });
        return base + (base.contains("?") ? "&" : "?") + q;
    }
 
    /** 순서를 지키는 Map 만들기: params("a", 1, "b", 2) */
    public static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}