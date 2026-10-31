package com.tripbiseo.after.service;

import org.springframework.stereotype.Service;

import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.dto.RetroStop;
import com.tripbiseo.live.service.TrackService;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ⑤ 회고. 여행·일정은 ②의 TripService, GPS는 ④의 TrackService로 읽어요 (남의 Mapper 직접 사용 안 함).
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/TripService.java (retro 메서드)
 */
@Service
@RequiredArgsConstructor
public class RetroServiceImpl implements RetroService {

    private final TripService tripService;
    private final TrackService trackService;

    /** 회고: 계획 vs 실제 + GPS 선 */
    // TODO: ⑤ 회고. 지금은 ②·④의 가짜 값을 그대로 옮겨 담기만 해요. arrivedAt이 있으면 visited +1, actualArrive = HH:mm,
    //       departedAt도 있으면 actualStay = 두 시각 사이 분. 참고: 예전 TripService.retro
    @Override
    public RetroResponse getRetro(long memberId, long tripId) {
        Trip trip = tripService.getTrip(memberId, tripId);
        RetroResponse response = new RetroResponse();
        response.setId(trip.getId());
        response.setTitle(trip.getTitle());
        response.setStartDate(trip.getStartDate());
        response.setEndDate(trip.getEndDate());
        for (Stop stop : trip.getStops()) {
            response.getStops().add(toRetroStop(stop));
        }
        response.setTrack(trackService.getTrackList(tripId));
        return response;
    }

    private static RetroStop toRetroStop(Stop stop) {
        RetroStop retro = new RetroStop();
        retro.setId(stop.getId());
        retro.setDay(stop.getDay());
        retro.setName(stop.getName());
        retro.setCategory(stop.getCategory());
        retro.setLat(stop.getLat());
        retro.setLng(stop.getLng());
        retro.setTime(stop.getTime());
        retro.setStay(stop.getStay());
        retro.setState(stop.getState());
        return retro;
    }
}
