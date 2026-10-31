package com.tripbiseo.common.ai;

import org.springframework.ai.tool.annotation.Tool;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.Forecast;
import com.tripbiseo.common.external.WeatherClient;

/**
 * Gemini가 필요할 때 부르는 날씨 도구. 인자가 없고, 위치는 서버가 들고 있어서 Gemini에는 좌표가 가지 않습니다.
 * 묻기 한 번마다 그 요청의 위치로 새로 만듭니다 (AiServiceImpl.askJsonWithWeather).
 */
public class WeatherTool {

    private final WeatherClient weatherClient;
    private final double lat;
    private final double lng;

    public WeatherTool(WeatherClient weatherClient, double lat, double lng) {
        this.weatherClient = weatherClient;
        this.lat = lat;
        this.lng = lng;
    }

    /** @Tool: Gemini에게 "이런 도구가 있다"고 알려 주는 표시. 설명을 보고 Gemini가 부를지 정함 */
    @Tool(name = "get_weather", description = "지금 여행 중인 곳의 오늘 시간대별 강수확률·강수형태·기온 (기상청 단기예보)")
    public Forecast getWeather() {
        try {
            return weatherClient.getForecast(lat, lng);
        } catch (ApiException e) {
            Forecast failed = new Forecast();
            failed.setNote("날씨를 가져오지 못했어요. 날씨 없이 판단하세요.");
            return failed;
        }
    }
}
