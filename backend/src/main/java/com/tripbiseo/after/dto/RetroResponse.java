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
    private Integer people;                            // 여행 인원 (정산 1인당 금액에 씀, 11/07)
    private int visited;                               // 실제 도착 기록(arrivedAt)이 있는 칸 수
    private List<RetroStop> stops = new ArrayList<>();
    private List<TrackPoint> track = new ArrayList<>(); // GPS 점 (기록 순)
    private int movedMeters; // 이동 거리 (m)
    private List<ExtraStop> extraStops = new ArrayList<>(); // 계획 없이 머문 곳 (GPS로 찾음). 없으면 빈 목록
}
