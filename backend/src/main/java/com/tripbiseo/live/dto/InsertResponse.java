package com.tripbiseo.live.dto;

import com.tripbiseo.trip.dto.Trip;

import lombok.Data;

/** POST /live/insert 응답 */
@Data
public class InsertResponse {
    private Trip trip;
    private String message; // "카페OO을(를) 다음 일정으로 넣었어요. 세종문화회관에는 12분 여유가 있어요."
}
