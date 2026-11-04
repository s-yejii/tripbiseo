package com.tripbiseo.plan.dto;

import java.util.ArrayList;
import java.util.List;

import com.tripbiseo.trip.dto.PlaceCard;
import com.tripbiseo.trip.dto.Stop;

import lombok.Data;

/** POST /api/plans/generate 응답 (저장 안 한 초안). 화면이 그대로 ②의 POST /api/trips로 저장 */
@Data
public class PlanGenerateResponse {
    private String title;           // 60자까지
    private String startDate;
    private String endDate;
    private Integer people;
    private List<String> transports = new ArrayList<>(); // 조건에 없으면 [] (그대로 저장하면 400)
    private List<Stop> stops = new ArrayList<>();        // day → time 순, id·tripId·mode·memo·arrivedAt·departedAt은 null
    private List<PlanNote> notes = new ArrayList<>();
    private List<PlaceCard> missed = new ArrayList<>();  // 꼭 갈 곳인데 일정에 못 넣은 곳 (못 찾은 곳은 이름만)
}
