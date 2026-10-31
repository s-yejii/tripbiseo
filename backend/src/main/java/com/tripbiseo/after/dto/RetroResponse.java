package com.tripbiseo.after.dto;

import java.util.ArrayList;
import java.util.List;

import com.tripbiseo.live.dto.TrackPoint;

import lombok.Data;

/** GET /api/trips/{id}/retro 응답: 계획 vs 실제 + GPS 선 */
@Data
public class RetroResponse {
    private Long id;
    private String title;
    private String startDate;
    private String endDate;
    private int visited;                               // 실제 도착 기록(arrivedAt)이 있는 칸 수
    private List<RetroStop> stops = new ArrayList<>();
    private List<TrackPoint> track = new ArrayList<>(); // GPS 점 (기록 순)
}
