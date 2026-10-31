package com.tripbiseo.after.service;

import org.springframework.stereotype.Service;

import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.dto.RetroStop;
import com.tripbiseo.live.service.TrackService;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RetroServiceImpl implements RetroService {

    private final TripService tripService;
    private final TrackService trackService;

    /** 회고: 계획 vs 실제 + GPS 선 */

    @Override
    public RetroResponse getRetro(long memberId, long tripId) {
        Trip trip = tripService.getTrip(memberId, tripId);
        RetroResponse response = new RetroResponse();
        response.setId(trip.getId());
        response.setTitle(trip.getTitle());
        response.setStartDate(trip.getStartDate());
        response.setEndDate(trip.getEndDate());
        int visited = 0;
        for (Stop stop : trip.getStops()) {

            if (stop.getArrivedAt() != null) {
                visited++;
            }
            response.getStops().add(toRetroStop(stop));
        }
        response.setVisited(visited);
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

        if (stop.getArrivedAt() != null) {
            LocalDateTime arrived = LocalDateTime.parse(stop.getArrivedAt());

            // 시각만 꺼내 앞 5글자만 잘라 적는 코드입니다.
            retro.setActualArrive(arrived.toLocalTime().toString().substring(0, 5));

            if (stop.getDepartedAt() != null) {

                LocalDateTime departed = LocalDateTime.parse(stop.getDepartedAt());
                // ChronoUnit.MINUTES.between: 두 시각 사이가 몇 분인지 계산 (long으로 돌려줘서 int로 바꿈)
                // 출발 도착 시간을 계산해서 그렇게 함.

                retro.setActualStay((int) ChronoUnit.MINUTES.between(arrived, departed));
            }

        }

        return retro;
    }
}
