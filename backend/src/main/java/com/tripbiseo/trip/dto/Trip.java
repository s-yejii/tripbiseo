package com.tripbiseo.trip.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/** 여행 (공통 데이터 Trip, DB trip). ①~⑤가 같이 쓰는 모양이라 바꾸기 전에 docs/API.md "서로 맞춰야 하는 API"를 봐요 */
@Data
public class Trip {
    private Long id;
    private Long memberId;          // 요청에 보내도 무시하고 로그인한 회원으로 채움
    private String title;           // 1~60자
    private String startDate;       // yyyy-MM-dd
    private String endDate;         // yyyy-MM-dd, 기간 1~30일
    private Integer people;         // 1~20
    private List<String> transports = new ArrayList<>(); // bus, subway, walk, taxi, car
    private String status;          // planned / ongoing / done
    private Boolean archived;       // 보관함에 있나 (planned인데 시작한 적 있음)
    private String deletedAt;       // 휴지통에 지운 시각 yyyy-MM-ddTHH:mm:ss (휴지통 목록에서만 값이 있음)
    private Integer stopCount;      // 목록(GET /api/trips)에서만 값이 있음
    // 일정 점검 때만 (저장 안 함, 11/09 ② 찬기 OK): AI 일정의 "어디서 출발해요?"에서 고른 곳 → 1일차 첫 장소까지 길안내
    private String originName;
    private Double originLat;
    private Double originLng;
    private List<Stop> stops = new ArrayList<>(); // 목록에서는 항상 [], 상세에서는 day → time → id 순
}
