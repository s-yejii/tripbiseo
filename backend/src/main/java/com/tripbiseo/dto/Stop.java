package com.tripbiseo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 여행 일정의 장소 한 칸 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stop {
    public Long id;
    public Long tripId;
    public Integer day;        // 1일차부터
    public String placeId;     // retrip_place.id (DB에 없는 장소면 null)
    public String name;
    public String category;
    public String address;
    public Double lat;
    public Double lng;
    public String time;        // 시작 시각 HH:mm
    public Integer stay;       // 머무는 시간(분)
    public Boolean fixed;      // 늦으면 안 되는 일정
    public String mode;        // 앞 장소에서 오는 수단 walk / transit / taxi (null이면 거리로 자동)
    public String memo;
    public String state;       // todo / done / skipped
    public String arrivedAt;   // GPS 도착 시각
    public String departedAt;

    public Stop copy() {
        Stop s = new Stop();
        s.id = id; s.tripId = tripId; s.day = day; s.placeId = placeId; s.name = name; s.category = category;
        s.address = address; s.lat = lat; s.lng = lng; s.time = time; s.stay = stay; s.fixed = fixed; s.mode = mode;
        s.memo = memo; s.state = state; s.arrivedAt = arrivedAt; s.departedAt = departedAt;
        return s;
    }
}
