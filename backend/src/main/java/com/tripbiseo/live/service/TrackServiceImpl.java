package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.live.dto.TrackPoint;
import com.tripbiseo.live.dto.TrackRequest;
import com.tripbiseo.live.mapper.TrackMapper;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ GPS. 지금은 가짜 값이에요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (track 메서드)
 */
@Service
@RequiredArgsConstructor
public class TrackServiceImpl implements TrackService {

    private static final int ARRIVE_RADIUS_M = 150; // 이 안에 들어오면 도착
    private static final int LEAVE_RADIUS_M = 200;  // 도착한 칸에서 이 밖으로 두 번 연속이면 출발

    private final TrackMapper trackMapper; // TODO: ④ GPS SQL (insertTrack·selectLastTrack …)
    private final TripService tripService; // TODO: ④ 오늘 칸 읽기(getTrip), 도착·출발 기록(updateStop 또는 ②에게 전용 메서드 요청)

    /** GPS 한 점 저장 + 도착·출발 판정 */
    // TODO: ④ GPS 저장. 공통 오류 순서(위치 → 404 → ongoing 아님) → 바로 앞 점(selectLastTrack) → insertTrack →
    //       오늘 todo 칸 중 Geo.distanceMeters ≤ ARRIVE_RADIUS_M면 arrivedAt, 도착한 칸에서 지금·앞 점 둘 다 > LEAVE_RADIUS_M면 departedAt + done.
    //       참고: 예전 LiveService.track. 주의: trip_stop은 ②(공통) 테이블이라 직접 UPDATE하지 말고 TripService로.
    //       두 점 사이 거리는 Java Geo 도우미로 OK (이미 손에 있는 점). 여러 번 쓰니 @Transactional 유지
    @Override
    @Transactional
    public void createTrack(long memberId, long tripId, TrackRequest request) {
    }

    /** 여행의 GPS 점 */
    // TODO: ④ GPS 선. selectTrackList(tripId) — day = 기록 날짜 − 시작 날짜 + 1, time = HH:mm. 참고: 예전 TripMapper.xml findTracks
    @Override
    public List<TrackPoint> getTrackList(long tripId) {
        List<TrackPoint> points = new ArrayList<>();
        points.add(point(37.5794, 126.9768, "10:03"));
        points.add(point(37.5788, 126.9772, "10:05"));
        return points;
    }

    /** 회원의 위치 기록 모두 지우기 */
    // TODO: ④ 위치 기록 지우기. deleteTrackList(memberId) — trip_track을 trip과 JOIN해서 내 여행 것만
    @Override
    public void deleteTrackList(long memberId) {
    }

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static TrackPoint point(double lat, double lng, String time) {
        TrackPoint point = new TrackPoint();
        point.setLat(lat);
        point.setLng(lng);
        point.setDay(1);
        point.setTime(time);
        return point;
    }
}
