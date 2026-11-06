package com.tripbiseo.live.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.StopAreaMeters;
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

    private static final int ARRIVE_RADIUS_M = 150; // 일반 칸: 최근 점이 다 이 안 + 멈춰 있으면 도착
    private static final int LEAVE_RADIUS_M = 200;  // 일반 칸: 지금 점·앞 점 둘 다 이 밖이면 출발
    private static final int AREA_ARRIVE_M = 30;    // 경계 칸(공원): 최근 점이 다 경계 이 안 + 걷는 속도면 도착
    private static final int AREA_LEAVE_M = 80;     // 경계 칸(공원): 지금 점·앞 점 둘 다 경계 이 밖이면 출발
    private static final int STAY_POINTS = 4;       // 도착은 최근 4점(20초마다 → 약 1분)으로 판정
    private static final int STAY_SECONDS = 120;    // 이보다 오래된 점은 안 씀 (GPS가 끊겼다 오면 예전 위치가 섞임)
    private static final int STAY_MOVE_M = 40;      // 일반 칸: 첫 점~마지막 점이 이 안이면 멈춰 있음 (걸으면 1분에 약 75m)
    private static final int WALK_STEP_M = 60;      // 경계 칸: 점 사이(약 20초)가 다 이 안이면 걷는 속도 (버스·차는 100m 넘음)
    private static final int MAX_ACCURACY_M = 100;  // GPS 오차가 이보다 크면 저장만 하고 판정 안 함

    private final TrackMapper trackMapper;
    private final TripService tripService;         // 도착·출발 기록은 trip_stop 주인(②)의 메서드로
    private final LiveTripReader liveTripReader;   // 공통 오류 검사 + 진행 중인 여행 (④ 도우미)

    /** GPS 한 점 저장 + 도착·출발 판정 */
    // 규칙: 지금 있는 칸은 하나만. 새 칸에 도착하면 전 칸은 다녀옴.
    //   도착: 최근 4점(2분 안)으로 봄. 1순위 일반 칸 = 다 150m 안 + 멈춰 있음(40m 안)
    //                                  2순위 경계 칸(공원) = 다 경계 30m 안 + 걷는 속도(점 사이 60m 안)
    //   출발: 지금 점·앞 점 둘 다 지금 칸 밖 (일반 칸 200m, 경계 칸은 경계 80m)
    @Override
    @Transactional
    public void createTrack(long memberId, long tripId, TrackRequest request) {
        // 1. 공통 오류 검사 (위치 → 404 → 진행 중 아님). GPS를 저장하기 전에 해야 시작 안 한 여행에 점이 안 쌓임
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());

        // 2. 지금 점 저장
        trackMapper.insertTrack(tripId, request.getLat(), request.getLng(), request.getAccuracy());

        // 3. 판정할 수 없는 점이면 여기서 끝
        if (isInaccurate(request.getAccuracy())) {
            return;
        }

        // 4. 최근 점 (새 것부터, 방금 넣은 점이 0번). 2점이 안 되면 판정 안 함
        List<TrackPoint> recent = trackMapper.selectRecentTrackList(tripId, STAY_POINTS, STAY_SECONDS, MAX_ACCURACY_M);
        if (recent.size() < 2) {
            return;
        }

        // 5. 도착·출발 판정
        checkArriveOrLeave(memberId, tripId, getTodayStops(trip), recent, getAreaMetersList(tripId, recent));
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

    /** 최근 점마다 "경계 칸 id → 경계까지 거리(m)" (경계 안이면 0). 순서는 recent와 같음 */
    private List<Map<Long, Double>> getAreaMetersList(long tripId, List<TrackPoint> recent) {
        List<Map<Long, Double>> areaList = new ArrayList<>();
        for (TrackPoint point : recent) {
            Map<Long, Double> areaMeters = new HashMap<>();
            // 경계 칸이 있는지는 점과 상관없음 → 첫 점에서 없으면 나머지 점은 안 물어봄
            boolean hasAreaStop = areaList.isEmpty() || !areaList.get(0).isEmpty();
            if (hasAreaStop) {
                for (StopAreaMeters row : trackMapper.selectAreaMetersList(tripId, point.getLat(), point.getLng())) {
                    areaMeters.put(row.getStopId(), row.getMeters());
                }
            }
            areaList.add(areaMeters);
        }
        return areaList;
    }

    /** 새 칸에 도착했으면 도착 기록(전 칸은 다녀옴), 아니면 지금 칸에서 출발했는지 확인 */
    private void checkArriveOrLeave(long memberId, long tripId, List<Stop> stops, List<TrackPoint> recent,
                                    List<Map<Long, Double>> areaList) {
        Stop current = findCurrentStop(stops);
        Stop arrived = findArrivedStop(stops, recent, areaList);

        // 새 칸 도착
        if (arrived != null && arrived != current) {
            if (current != null) {
                leaveStop(memberId, tripId, current);
            }
            arriveStop(memberId, tripId, arrived);
            return;
        }
        // 출발: 지금 점(0번)·앞 점(1번) 둘 다 지금 칸 밖
        if (current != null && isOutside(current, recent.get(0), areaList.get(0))
                && isOutside(current, recent.get(1), areaList.get(1))) {
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

    /** 도착한 칸: 1순위 멈춰 있는 일반 칸, 2순위 걸어 다니는 경계 칸. 최근 점이 4개가 안 되면 null */
    private Stop findArrivedStop(List<Stop> stops, List<TrackPoint> recent, List<Map<Long, Double>> areaList) {
        if (recent.size() < STAY_POINTS) {
            return null;
        }
        // 공원 안 카페처럼 더 구체적인 곳을 먼저 봄
        Stop pointStop = findStayingPointStop(stops, recent, areaList.get(0));
        if (pointStop != null) {
            return pointStop;
        }
        return findWalkingAreaStop(stops, recent, areaList);
    }

    /** 일반 칸 중 최근 점이 다 150m 안인 제일 가까운 칸. 멈춰 있지 않으면(걸어서 지나가는 중) null */
    private Stop findStayingPointStop(List<Stop> stops, List<TrackPoint> recent, Map<Long, Double> areaMeters) {
        if (!isStaying(recent)) {
            return null;
        }
        Stop nearest = null;
        double nearestMeters = Double.MAX_VALUE;
        for (Stop stop : stops) {
            if (hasArea(stop, areaMeters) || !isAllWithin(stop, recent)) {
                continue;
            }
            double meters = pointMeters(stop, recent.get(0));
            if (meters < nearestMeters) {
                nearest = stop;
                nearestMeters = meters;
            }
        }
        return nearest;
    }

    /** 경계 칸 중 최근 점이 다 경계 30m 안인 칸. 걷는 속도가 아니면(차·버스로 지나감) null */
    private Stop findWalkingAreaStop(List<Stop> stops, List<TrackPoint> recent, List<Map<Long, Double>> areaList) {
        if (!isWalking(recent)) {
            return null;
        }
        for (Stop stop : stops) {
            if (hasArea(stop, areaList.get(0)) && isAllInsideArea(stop, areaList)) {
                return stop;
            }
        }
        return null;
    }

    /** 멈춰 있는지: 제일 새 점과 제일 오래된 점 사이가 40m 안 */
    private boolean isStaying(List<TrackPoint> recent) {
        TrackPoint newest = recent.get(0);
        TrackPoint oldest = recent.get(recent.size() - 1);
        return Geo.distanceMeters(newest.getLat(), newest.getLng(), oldest.getLat(), oldest.getLng()) <= STAY_MOVE_M;
    }

    /** 걷는 속도인지: 이웃한 점 사이(약 20초)가 다 60m 안 */
    private boolean isWalking(List<TrackPoint> recent) {
        for (int i = 0; i + 1 < recent.size(); i++) {
            TrackPoint a = recent.get(i);
            TrackPoint b = recent.get(i + 1);
            if (Geo.distanceMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng()) > WALK_STEP_M) {
                return false;
            }
        }
        return true;
    }

    /** 일반 칸: 최근 점이 다 150m 안인지 */
    private boolean isAllWithin(Stop stop, List<TrackPoint> recent) {
        for (TrackPoint point : recent) {
            if (pointMeters(stop, point) > ARRIVE_RADIUS_M) {
                return false;
            }
        }
        return true;
    }

    /** 경계 칸: 최근 점이 다 경계 30m 안인지 */
    private boolean isAllInsideArea(Stop stop, List<Map<Long, Double>> areaList) {
        for (Map<Long, Double> areaMeters : areaList) {
            Double meters = areaMeters.get(stop.getId());
            if (meters == null || meters > AREA_ARRIVE_M) {
                return false;
            }
        }
        return true;
    }

    /** 이 점이 칸 밖인지 (일반 칸 200m, 경계 칸은 경계 80m) */
    private boolean isOutside(Stop stop, TrackPoint point, Map<Long, Double> areaMeters) {
        if (hasArea(stop, areaMeters)) {
            return areaMeters.get(stop.getId()) > AREA_LEAVE_M;
        }
        return pointMeters(stop, point) > LEAVE_RADIUS_M;
    }

    /** 경계(place_area)가 있는 칸인지 */
    private boolean hasArea(Stop stop, Map<Long, Double> areaMeters) {
        return stop.getId() != null && areaMeters.containsKey(stop.getId());
    }

    /** 점에서 칸 대표 점까지 거리(m) */
    private double pointMeters(Stop stop, TrackPoint point) {
        return Geo.distanceMeters(point.getLat(), point.getLng(), stop.getLat(), stop.getLng());
    }

    /** 칸 도착 기록 (arrived_at = 지금) */
    private void arriveStop(long memberId, long tripId, Stop stop) {
        tripService.arriveStop(memberId, tripId, stop.getId());
    }

    /** 칸 출발 기록 (departed_at = 지금, state = done) */
    private void leaveStop(long memberId, long tripId, Stop stop) {
        tripService.departStop(memberId, tripId, stop.getId());
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
