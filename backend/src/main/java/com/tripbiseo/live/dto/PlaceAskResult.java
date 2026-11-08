package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** AI에게 묻기(Text-to-SQL)로 장소를 찾은 결과 */
@Data
public class PlaceAskResult {
    private boolean fallback;      // true: AI로 찾지 못함 → 예전처럼 반경을 골라 찾게 함
    private String message;        // 화면 안내 한 줄 (예: "4곳을 찾았어요", "가격 정보가 없어서 …")
    private String explain;        // 어떻게 찾았는지 한 줄 (Gemini가 씀)
    private String sql;            // AI가 쓴 SQL. 빈칸(:route 등) 그대로라 좌표는 없음. "이렇게 찾았어요"에 보여 줌
    private Integer slack;         // 다음 일정까지 지금 여유(분). 없으면 null
    private String band;           // 지도에 그릴 가는 길 띠 (GeoJSON). 가는 길로 찾지 않았으면 null
    private List<AskedPlace> places = new ArrayList<>();
}
