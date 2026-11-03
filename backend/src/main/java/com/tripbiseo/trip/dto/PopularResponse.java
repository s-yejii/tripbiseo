package com.tripbiseo.trip.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** GET /api/places/popular 응답 */
@Data
public class PopularResponse {
    private String scope; // near(기준점 반경) / wide(5km로 넓힘) / seoul(시범 지역 전체)
    private List<PopularPlace> places = new ArrayList<>();
}
