package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⑤ CODEF 개인카드 승인내역 (11/07).
 *   1) client id·secret으로 토큰(출입증)을 받아 7일 동안 다시 씀
 *   2) 승인내역 API에 "URL 인코딩한 JSON"을 보내고, 응답도 URL 인코딩돼서 와서 풀어서 읽음 (CODEF 규칙)
 * 키는 application-local.properties의 CODEF_CLIENT_ID·CODEF_CLIENT_SECRET (코드·채팅에 쓰지 않음).
 * 샌드박스는 값이 "****"로 가려져 오니 연결 확인용, 진짜 내역은 데모(demo)부터.
 */
@Component
public class CodefClient {

    private static final Logger log = LoggerFactory.getLogger(CodefClient.class);

    private static final String OAUTH_URL = "https://oauth.codef.io/oauth/token";
    private static final String SANDBOX_HOST = "https://sandbox.codef.io";
    private static final String DEMO_HOST = "https://development.codef.io";
    private static final String PRODUCT_HOST = "https://api.codef.io";
    private static final String APPROVAL_PATH = "/v1/kr/card/p/account/approval-list";
    private static final String SUCCESS_CODE = "CF-00000";
    private static final long TOKEN_MARGIN_MS = 60L * 60 * 1000; // 만료 1시간 전부터는 새 토큰
    private static final String NORMAL = "0";                    // resCancelYN "0" 정상
    private static final String PARTIAL_CANCEL = "2";            // "2" 부분취소 (금액에서 취소 금액을 뺌)

    @Value("${CODEF_CLIENT_ID:}")
    private String clientId;
    @Value("${CODEF_CLIENT_SECRET:}")
    private String clientSecret;
    @Value("${CODEF_SERVICE_TYPE:sandbox}")
    private String serviceType; // sandbox / demo / product

    // RestClient: Spring에 들어 있는 HTTP 호출 도구
    private final RestClient restClient = RestClient.create();
    // ObjectMapper: JSON 글자 ↔ JsonNode 바꾸기
    private final ObjectMapper objectMapper;

    private String token;        // 받아 둔 토큰. 없으면 null
    private long tokenExpiresAt; // 토큰이 끝나는 때 (1970년부터 ms)

    public CodefClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 키가 들어 있나 */
    public boolean isReady() {
        return !clientId.isBlank() && !clientSecret.isBlank();
    }

    /** 진짜 내역을 주는 환경인가 (샌드박스는 값이 가려져 와서 결제 내역으로 못 씀) */
    public boolean isRealData() {
        return isReady() && !"sandbox".equals(serviceType);
    }

    /** 개인카드 승인내역 (startDate·endDate: yyyyMMdd), 과거순. 정상 승인만 돌려줌 */
    public List<CodefApproval> getApprovalList(String organization, String connectedId, String startDate, String endDate) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organization", organization);
        body.put("connectedId", connectedId);
        body.put("startDate", startDate);
        body.put("endDate", endDate);
        body.put("orderBy", "1");             // 과거순 (타임라인 순서와 같게)
        body.put("inquiryType", "1");         // 전체 조회 (카드별이면 카드 번호까지 필요)
        body.put("memberStoreInfoType", "1"); // 가맹점 주소·업종 포함
        JsonNode response = post(APPROVAL_PATH, body);
        return toApprovalList(response.path("data"));
    }

    // 승인내역 data → 정상 승인만 골라 담기. 결과가 하나면 data가 목록이 아니라 객체 하나로 옴 (샌드박스에서 확인)
    private List<CodefApproval> toApprovalList(JsonNode data) {
        List<JsonNode> rows = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode row : data) rows.add(row);
        } else if (data.isObject()) {
            rows.add(data);
        }
        List<CodefApproval> list = new ArrayList<>();
        for (JsonNode row : rows) {
            CodefApproval approval = toApproval(row);
            if (approval != null) list.add(approval);
        }
        return list;
    }

    // 한 줄 → CodefApproval. 취소·거절이거나 값이 가려져 있으면(샌드박스 "****") null
    private CodefApproval toApproval(JsonNode row) {
        String cancel = row.path("resCancelYN").asText("");
        if (!NORMAL.equals(cancel) && !PARTIAL_CANCEL.equals(cancel)) return null; // "1" 취소, "3" 거절
        String date = row.path("resUsedDate").asText("");
        String time = row.path("resUsedTime").asText("");
        if (!date.matches("\\d{8}") || !time.matches("\\d{4,6}")) return null; // 숫자가 아니면(가려진 값) 건너뜀
        int amount = toWon(row);
        if (amount <= 0) return null;
        CodefApproval approval = new CodefApproval();
        approval.setUsedDate(date);
        approval.setUsedTime(time);
        approval.setStoreName(row.path("resMemberStoreName").asText("").trim());
        approval.setAmount(amount);
        approval.setApprovalNo(row.path("resApprovalNo").asText(""));
        approval.setStoreAddress(row.path("resMemberStoreAddr").asText(""));
        approval.setStoreType(row.path("resMemberStoreType").asText(""));
        return approval;
    }

    // 금액(원). 해외면 원화 금액(resKRWAmt), 부분취소면 취소 금액을 뺌. 숫자가 아니면 0
    private static int toWon(JsonNode row) {
        String text = row.path("resUsedAmount").asText("");
        if ("2".equals(row.path("resHomeForeignType").asText("")) && !row.path("resKRWAmt").asText("").isBlank()) {
            text = row.path("resKRWAmt").asText("");
        }
        int amount = toNumber(text);
        if (PARTIAL_CANCEL.equals(row.path("resCancelYN").asText(""))) amount -= toNumber(row.path("resCancelAmount").asText(""));
        return amount;
    }

    // "12,400" / "12400.0" → 12400. 숫자가 없으면 0
    private static int toNumber(String text) {
        String digits = text.replace(",", "");
        if (digits.contains(".")) digits = digits.substring(0, digits.indexOf('.'));
        if (!digits.matches("-?\\d+")) return 0;
        return Integer.parseInt(digits);
    }

    // CODEF 상품 API 부르기: 본문은 URL 인코딩한 JSON, 응답도 URL 인코딩 → 풀어서 JSON으로. 실패하면 502
    private JsonNode post(String path, Map<String, Object> body) {
        if (!isReady()) throw fail(502, "카드사 연결 키(CODEF)가 없어요.");
        try {
            String encoded = URLEncoder.encode(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8);
            String raw = restClient.post().uri(toHost() + path)
                    .header("Authorization", "Bearer " + getToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(encoded)
                    .retrieve().body(String.class);
            JsonNode json = objectMapper.readTree(URLDecoder.decode(raw, StandardCharsets.UTF_8));
            String code = json.path("result").path("code").asText("");
            if (!SUCCESS_CODE.equals(code)) {
                log.warn("CODEF 실패 {} {}", code, json.path("result").path("message").asText(""));
                throw fail(502, "카드사 내역을 불러오지 못했어요. (" + code + ")");
            }
            return json;
        } catch (RestClientException e) {
            log.warn("CODEF 연결 실패: {}", e.getMessage());
            throw fail(502, "카드사 서버(CODEF)에 연결하지 못했어요.");
        }
    }

    // 토큰: 있고 아직 안 끝났으면 다시 쓰고, 아니면 새로 받음 (client id:secret을 Base64로 → Basic 인증)
    private String getToken() {
        if (token != null && System.currentTimeMillis() < tokenExpiresAt - TOKEN_MARGIN_MS) return token;
        String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String raw = restClient.post().uri(OAUTH_URL)
                .header("Authorization", "Basic " + basic)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&scope=read")
                .retrieve().body(String.class);
        JsonNode json = objectMapper.readTree(raw);
        token = json.path("access_token").asText(null);
        tokenExpiresAt = System.currentTimeMillis() + json.path("expires_in").asLong(0) * 1000;
        if (token == null) throw fail(502, "카드사 연결 키(CODEF)가 거절됐어요.");
        return token;
    }

    // 환경별 주소
    private String toHost() {
        if ("product".equals(serviceType)) return PRODUCT_HOST;
        if ("demo".equals(serviceType)) return DEMO_HOST;
        return SANDBOX_HOST;
    }
}
