package com.tripbiseo.after.service;

import org.springframework.stereotype.Service;

import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.dto.RetroStop;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.TrackPoint;
import com.tripbiseo.live.service.TrackService;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

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
        int visited = 0; // 실제로 다녀온 장소 수
        for (Stop stop : trip.getStops()) {

            // 도착 기록이 있으면 다녀온 곳으로 셈
            if (stop.getArrivedAt() != null) {
                visited++;
            }
            response.getStops().add(toRetroStop(stop));
        }
        List<TrackPoint> track = trackService.getTrackList(tripId); // gps 점 기록 
        response.setTrack(track);
        response.setMovedMeters(sumMovedMeters(track));
        return response;
    }

    // GPS 점 사이 거리를 기록 순서대로 더함(m). 탈것으로 이동한 거리도 포함
    // TODO: 4번 진짜 GPS가 들어오면 기록 간격을 보고, 걷기보다 빠른 구간은 빼서 "걸은 거리"로 바꿀지 정하기
    private static int sumMovedMeters(List<TrackPoint> track) {
        double total = 0;
        for (int i = 1; i< track.size(); i++) {
            TrackPoint before = track.get(i - 1);
            TrackPoint now = track.get(i);
            total += Geo.distanceMeters(before.getLat(), before.getLng(), now.getLat(), now.getLng());
        }
           return (int) Math.round(total);
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

        // 도착 기록이 있을 때만 실제 도착 시각을 채움. 없으면 null로 둠
        if (stop.getArrivedAt() != null) {
            LocalDateTime arrived = LocalDateTime.parse(stop.getArrivedAt());

            // 시각만 꺼내 앞 5글자만 잘라 적는 코드입니다.
            retro.setActualArrive(arrived.toLocalTime().toString().substring(0, 5));

            // 출발 기록까지 있어야 머문 시간을 계산할 수 있음
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
