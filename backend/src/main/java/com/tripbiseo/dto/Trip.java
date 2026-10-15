package com.tripbiseo.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 여행. 화면과 주고받는 모양 그대로 (MyBatis·JSON 모두 public 필드를 씀) */
@JsonIgnoreProperties(ignoreUnknown = true) // 화면이 보낸 계산용 값(stopCount 등)은 무시
public class Trip {
    public Long id;
    public Long memberId;
    public String title;
    public String startDate;   // yyyy-MM-dd
    public String endDate;
    public Integer people;
    public List<String> transports = new ArrayList<>(); // bus, subway, walk, taxi, car
    public String status;      // planned / ongoing / done
    public Integer stopCount;  // 목록에서만
    public List<Stop> stops = new ArrayList<>();
}
