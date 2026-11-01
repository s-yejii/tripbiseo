package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.TrackPoint;
import com.tripbiseo.live.dto.TrackRequest;
import com.tripbiseo.live.mapper.TrackMapper;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ GPS 저장·도착 판정, GPS 기록 조회·삭제.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (track 메서드)
 */
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    private static final int ARRIVE_RADIUS_M = 150; // 지금 점·앞 점 둘 다 이 안이면 도착
    private static final int LEAVE_RADIUS_M = 200;  // 지금 칸에서 지금 점·앞 점 둘 다 이 밖이면 출발
    private static final int MAX_ACCURACY_M = 100;  // GPS 오차가 이보다 크면 저장만 하고 판정 안 함

    private final TrackMapper trackMapper;
    private final TripService tripService;

    /** GPS 한 점 저장 + 도착·출발 판정 */
    // 규칙: 지금 있는 칸은 하나만. 제일 가까운 칸이 바뀌면(150m 안 두 번) 새 칸 도착 + 전 칸 다녀옴.
    //       근처에 새 칸이 없으면 전 칸에서 200m 밖 두 번일 때 다녀옴.
    @Override
    @Transactional
    public void createTrack(long memberId, long tripId, TrackRequest request) {
        // 1. 위치 검사 (API.md 공통 오류 1번)
        if (request.getLat() == null || request.getLng() == null) {
            throw fail(400, "내 위치를 먼저 찾아 주세요.");
        }
        // TODO: ④ 2·3번 검사(404, ongoing 아님)는 ②의 진짜 getTrip이 나오면 추가
        Trip trip = tripService.getTrip(memberId, tripId);

        // 2. 바로 앞 점을 먼저 읽고(저장 뒤에 읽으면 방금 넣은 점이 나옴) 지금 점 저장
        TrackPoint before = trackMapper.selectLastTrack(tripId);
        trackMapper.insertTrack(tripId, request.getLat(), request.getLng(), request.getAccuracy());

        // 3. 판정할 수 없는 점이면 여기서 끝
        if (before == null || isInaccurate(request.getAccuracy())) {
            return;
        }
        TrackPoint now = new TrackPoint();
        now.setLat(request.getLat());
        now.setLng(request.getLng());

        // 4. 도착·출발 판정
        checkArriveOrLeave(memberId, tripId, getTodayStops(trip), now, before);
    }

    // ---------------------------------------------------------------- 도착·출발 판정 도우미

    /** 오차가 너무 큰 점인지 (accuracy를 안 보냈으면 믿고 판정) */
    private boolean isInaccurate(Double accuracy) {
        return accuracy != null && accuracy > MAX_ACCURACY_M;
    }

    /** 오늘 칸 중 아직 다녀오지 않은 칸 (todo + 좌표 있음) */
    private List<Stop> getTodayStops(Trip trip) {
        // ChronoUnit.DAYS.between: 두 날짜 사이가 며칠인지 (시작일이면 0 → 1일째)
        long today = ChronoUnit.DAYS.between(LocalDate.parse(trip.getStartDate()), Geo.now().toLocalDate()) + 1;
        List<Stop> stops = new ArrayList<>();
        for (Stop stop : trip.getStops()) {
            boolean isToday = stop.getDay() != null && stop.getDay() == today;
            boolean hasPlace = stop.getLat() != null && stop.getLng() != null;
            if (isToday && hasPlace && "todo".equals(stop.getState())) {
                stops.add(stop);
            }
        }
        return stops;
    }

    /** 지금 칸(도착했고 아직 출발 안 함)과 제일 가까운 칸을 보고 도착·출발 기록 */
    private void checkArriveOrLeave(long memberId, long tripId, List<Stop> stops, TrackPoint now, TrackPoint before) {
        Stop current = findCurrentStop(stops);
        Stop nearest = findNearestStop(stops, now);

        // 새 칸 도착: 제일 가까운 칸이 지금 칸이 아니고, 지금 점·앞 점 둘 다 150m 안
        boolean isNewStop = nearest != null && nearest != current;
        if (isNewStop && isWithin(nearest, now, ARRIVE_RADIUS_M) && isWithin(nearest, before, ARRIVE_RADIUS_M)) {
            if (current != null) {
                leaveStop(memberId, tripId, current);
            }
            arriveStop(memberId, tripId, nearest);
            return;
        }
        // 출발: 지금 칸에서 지금 점·앞 점 둘 다 200m 밖
        if (current != null && !isWithin(current, now, LEAVE_RADIUS_M) && !isWithin(current, before, LEAVE_RADIUS_M)) {
            leaveStop(memberId, tripId, current);
        }
    }

    /** 도착했고 아직 출발 안 한 칸 (없으면 null) */
    private Stop findCurrentStop(List<Stop> stops) {
        for (Stop stop : stops) {
            if (stop.getArrivedAt() != null && stop.getDepartedAt() == null) {
                return stop;
            }
        }
        return null;
    }

    /** 지금 점에서 제일 가까운 칸 (지금 칸도 후보에 넣어야 "아직 여기 있음"을 알 수 있음) */
    private Stop findNearestStop(List<Stop> stops, TrackPoint now) {
        Stop nearest = null;
        double nearestMeters = Double.MAX_VALUE;
        for (Stop stop : stops) {
            double meters = Geo.distanceMeters(now.getLat(), now.getLng(), stop.getLat(), stop.getLng());
            if (meters < nearestMeters) {
                nearest = stop;
                nearestMeters = meters;
            }
        }
        return nearest;
    }

    /** 점이 칸에서 radius 미터 안인지 */
    private boolean isWithin(Stop stop, TrackPoint point, int radius) {
        return Geo.distanceMeters(point.getLat(), point.getLng(), stop.getLat(), stop.getLng()) <= radius;
    }

    /** 칸 도착 기록 (arrived_at = 지금) */
    private void arriveStop(long memberId, long tripId, Stop stop) {
        // TODO: 2번에게 요청함 — tripService.arriveStop(memberId, tripId, stop.getId());
    }

    /** 칸 출발 기록 (departed_at = 지금, state = done) */
    private void leaveStop(long memberId, long tripId, Stop stop) {
        // TODO: 2번에게 요청함 — tripService.departStop(memberId, tripId, stop.getId());
    }

    /** 여행의 GPS 점 */
    // 내 여행인지는 부르는 쪽(⑤ RetroService)이 getTrip으로 먼저 확인함
    @Override
    public List<TrackPoint> getTrackList(long tripId) {
        return trackMapper.selectTrackList(tripId);
    }

    /** 회원의 위치 기록 모두 지우기 */
    @Override
    public void deleteTrackList(long memberId) {
        trackMapper.deleteTrackList(memberId);
    }
}
