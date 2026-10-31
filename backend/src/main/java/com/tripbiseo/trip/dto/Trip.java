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
    private Integer stopCount;      // 목록(GET /api/trips)에서만 값이 있음
    private List<Stop> stops = new ArrayList<>(); // 목록에서는 항상 [], 상세에서는 day → time → id 순
}
