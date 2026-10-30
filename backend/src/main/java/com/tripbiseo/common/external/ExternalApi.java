package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 외부 API(TMAP·ODsay·기상청·TourAPI·카카오)를 부르는 곳 한 군데.
 * 실패는 모두 ApiException으로 바꿔 화면에 이유가 보이게 합니다.
 *   연결 실패 → 502, 시간 초과 → 504 "OO 서버에 연결하지 못했습니다"
 *   401/403 → 502 "OO 키가 거절되었습니다" / 그 밖의 오류 상태 → 502 "OO 응답 오류 상태코드"
 *   JSON이 아님(공공데이터 키 오류는 XML로 옴) → 502 + XML 안의 이유
 */
@Component
public class ExternalApi {

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final Pattern XML_REASON = Pattern.compile("<(?:returnAuthMsg|resultMsg)>([^<]+)<");

    /** 상태 코드와 응답 글자를 함께 담는 작은 상자 */
    private record RawResponse(int status, String body) {}

    // RestClient: Spring에 들어 있는 HTTP 호출 도구 (예전 직접 만든 Http 클래스 대신)
    private final RestClient restClient;
    // ObjectMapper: Spring에 들어 있는 JSON 도구 Jackson (예전 직접 만든 Json 클래스 대신)
    private final ObjectMapper objectMapper;

    public ExternalApi(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    /** base 주소 + 쿼리 값으로 주소 만들기 (한글·특수문자는 자동으로 인코딩) */
    public URI buildUrl(String base, Map<String, Object> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(base);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() != null) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        return builder.encode().build().toUri();
    }

    /** GET 호출 → JSON. label은 오류 문장에 들어갈 이름 (예: "기상청") */
    public JsonNode getJson(URI url, Map<String, String> headers, String label) {
        RawResponse response = send(label, () -> restClient.get().uri(url)
                .headers(h -> headers.forEach(h::set))
                .exchange((request, res) -> new RawResponse(res.getStatusCode().value(), readBody(res.getBody().readAllBytes()))));
        return check(response, label);
    }

    /** POST(JSON 본문) 호출 → JSON */
    public JsonNode postJson(String url, Map<String, String> headers, Object body, String label) {
        RawResponse response = send(label, () -> restClient.post().uri(url)
                .headers(h -> headers.forEach(h::set))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange((request, res) -> new RawResponse(res.getStatusCode().value(), readBody(res.getBody().readAllBytes()))));
        return check(response, label);
    }

    // 람다로 받은 호출을 실행하고, 네트워크 오류를 한국어 오류로 바꿈
    private RawResponse send(String label, Supplier<RawResponse> call) {
        try {
            return call.get();
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                throw fail(504, label + " 서버에 연결하지 못했습니다. (시간 초과)");
            }
            throw fail(502, label + " 서버에 연결하지 못했습니다. (네트워크)");
        }
    }

    private JsonNode check(RawResponse response, String label) {
        int status = response.status();
        if (status == 401 || status == 403) {
            throw fail(502, label + " 키가 거절되었습니다. 키와 사용 신청 상태를 확인하세요.");
        }
        if (status < 200 || status >= 300) {
            throw fail(502, label + " 응답 오류 " + status);
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (JacksonException e) {
            Matcher m = XML_REASON.matcher(response.body());
            throw fail(502, label + " 응답을 읽지 못했습니다." + (m.find() ? " (" + m.group(1) + ")" : ""));
        }
    }

    private static String readBody(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
