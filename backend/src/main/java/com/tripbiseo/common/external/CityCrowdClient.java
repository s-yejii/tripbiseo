package com.tripbiseo.common.external;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.net.URI;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.tripbiseo.common.util.TtlCache;

import tools.jackson.databind.JsonNode;

/**
 * 서울 실시간 도시데이터 - 인구 (서울 열린데이터광장 citydata_ppltn).
 * 주요 121장소 중 하나의 이름을 넣으면 지금 혼잡도 단계·인원 범위·12시간 예측을 돌려줌.
 * 장소 이름·코드 목록: 서울시 주요 121장소 목록.xlsx (예: 명동 관광특구, 남산공원, 익선동)
 */
@Component
public class CityCrowdClient {

    private static final String URL = "http://openapi.seoul.go.kr:8088";
    private static final long CACHE_MS = 5 * 60_000L; // 서울시가 약 5분마다 바꾸므로 5분 기억
    private static final String OK_CODE = "INFO-000";

    private final ExternalApi externalApi;
    private final TtlCache cache = new TtlCache(200);

    @Value("${SEOUL_API_KEY:}")
    private String apiKey;

    public CityCrowdClient(ExternalApi externalApi) {
        this.externalApi = externalApi;
    }

    /** 이 장소의 지금 혼잡도. 키가 없으면 503, 서울시 오류·없는 장소는 502 */
    public CityCrowd getCrowd(String areaName) {
        if (apiKey.isBlank()) {
            throw fail(503, "서울 열린데이터광장 키가 없어요.");
        }
        return cache.get("city:" + areaName, CACHE_MS, () -> callCrowd(areaName));
    }

    private CityCrowd callCrowd(String areaName) {
        // 주소 모양: /키/json/citydata_ppltn/1/5/장소이름 (장소 이름은 주소 안에 들어가서 한글 인코딩 필요)
        URI url = UriComponentsBuilder.fromUriString(URL)
                .pathSegment(apiKey, "json", "citydata_ppltn", "1", "5", areaName)
                .encode().build().toUri();
        JsonNode json = externalApi.getJson(url, Map.of(), "서울 도시데이터");

        String code = json.path("RESULT").path("RESULT.CODE").asString("");
        if (!code.equals(OK_CODE)) {
            throw fail(502, "서울 도시데이터 오류: " + json.path("RESULT").path("RESULT.MESSAGE").asString(code));
        }
        JsonNode rows = json.path("SeoulRtd.citydata_ppltn");
        if (rows.isEmpty()) {
            throw fail(502, "서울 도시데이터에 없는 장소예요: " + areaName);
        }
        return toCrowd(rows.get(0));
    }

    // 서울시 칸 이름(대문자) → 우리 DTO. 숫자도 글자로 와서 toNumber로 바꿈
    private CityCrowd toCrowd(JsonNode row) {
        CityCrowd crowd = new CityCrowd();
        crowd.setAreaCode(row.path("AREA_CD").asString(""));
        crowd.setAreaName(row.path("AREA_NM").asString(""));
        crowd.setLevel(row.path("AREA_CONGEST_LVL").asString(""));
        crowd.setMessage(row.path("AREA_CONGEST_MSG").asString(""));
        crowd.setPeopleMin(toNumber(row.path("AREA_PPLTN_MIN").asString("")));
        crowd.setPeopleMax(toNumber(row.path("AREA_PPLTN_MAX").asString("")));
        crowd.setTime(row.path("PPLTN_TIME").asString(""));

        for (JsonNode item : row.path("FCST_PPLTN")) {
            CityCrowd.Hour hour = new CityCrowd.Hour();
            hour.setTime(item.path("FCST_TIME").asString(""));
            hour.setLevel(item.path("FCST_CONGEST_LVL").asString(""));
            hour.setPeopleMin(toNumber(item.path("FCST_PPLTN_MIN").asString("")));
            hour.setPeopleMax(toNumber(item.path("FCST_PPLTN_MAX").asString("")));
            crowd.getForecast().add(hour);
        }
        return crowd;
    }

    private Integer toNumber(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
