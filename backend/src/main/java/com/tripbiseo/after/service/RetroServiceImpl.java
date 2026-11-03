package com.tripbiseo.after.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

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

    /** 회고: 계획 vs 실제 (도착 기록이 있으면 실제 도착 시각·머문 분) + GPS 선 */
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
            RetroStop retro = toRetroStop(stop);
            if (retro.getActualArrive() != null) {
                visited++;
            }
            response.getStops().add(retro);
        }
        response.setVisited(visited);
        response.setTrack(trackService.getTrackList(tripId));
        return response;
    }

    /** Stop → 회고 칸 (계획 값 + 실제 도착·머문 분) */
    private RetroStop toRetroStop(Stop stop) {
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
            // arrivedAt 형식 "2026-11-01T13:30:00" (②의 SQL) → LocalDateTime.parse로 바로 읽힘
            LocalDateTime arrived = LocalDateTime.parse(stop.getArrivedAt());
            retro.setActualArrive(arrived.toLocalTime().toString().substring(0, 5));
            if (stop.getDepartedAt() != null) {
                retro.setActualStay((int) ChronoUnit.MINUTES.between(arrived, LocalDateTime.parse(stop.getDepartedAt())));
            }
        }
        return retro;
    }
}
