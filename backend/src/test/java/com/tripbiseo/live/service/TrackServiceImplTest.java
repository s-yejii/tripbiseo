package com.tripbiseo.live.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.StopAreaMeters;
import com.tripbiseo.live.dto.TrackPoint;
import com.tripbiseo.live.dto.TrackRequest;
import com.tripbiseo.live.mapper.TrackMapper;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

/**
 * ④ GPS 도착·출발 판정 (DB 없이 Mapper를 가짜로)
 * 칸 1 = 여의도한강공원(경계 칸), 칸 2 = 스타벅스 여의도한강공원점, 칸 3 = 더현대(일반 칸)
 * 위도 0.0001 ≈ 11m, 경도 0.0001 ≈ 9m
 */
class TrackServiceImplTest {

    private static final long MEMBER_ID = 1L;
    private static final long TRIP_ID = 10L;

    private TrackMapper trackMapper;
    private TripService tripService;
    private TrackServiceImpl trackService;

    private Trip trip;
    private final Map<Double, Double> parkMeters = new HashMap<>(); // 점의 위도 → 그 점에서 공원 경계까지 거리(m)

    @BeforeEach
    void setUp() {
        trackMapper = mock(TrackMapper.class);
        tripService = mock(TripService.class);
        LiveTripReader liveTripReader = mock(LiveTripReader.class);
        trackService = new TrackServiceImpl(trackMapper, tripService, liveTripReader);

        trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setStartDate(Geo.now().toLocalDate().toString()); // 오늘이 1일째
        trip.getStops().add(stop(1L, "여의도한강공원", 37.5263998, 126.9336096));
        trip.getStops().add(stop(2L, "스타벅스 여의도한강공원점", 37.52893, 126.934541));
        trip.getStops().add(stop(3L, "더현대 서울", 37.5258, 126.9284));
        when(liveTripReader.getOngoingTrip(anyLong(), anyLong(), any(), any())).thenReturn(trip);

        // 경계 칸은 칸 1(공원)만. 점마다 parkMeters에 적은 거리를 돌려줌
        when(trackMapper.selectAreaMetersList(eq(TRIP_ID), anyDouble(), anyDouble())).thenAnswer(call -> {
            Double meters = parkMeters.get((Double) call.getArgument(1));
            StopAreaMeters row = new StopAreaMeters();
            row.setStopId(1L);
            row.setMeters(meters == null ? 9999.0 : meters);
            return List.of(row);
        });
    }

    @Test
    void 공원_먼_쪽에서_산책하면_공원_도착() {
        // 대표 점에서 2km 넘게 떨어진 서쪽 끝, 20초에 약 25m씩 걸음
        sendPark(new double[][] { { 37.5338, 126.9104 }, { 37.53358, 126.9104 }, { 37.53336, 126.9104 }, { 37.53314, 126.9104 } }, 0);
        verify(tripService).arriveStop(MEMBER_ID, TRIP_ID, 1L);
    }

    @Test
    void 공원_옆을_버스로_지나가면_도착_아님() {
        // 경계 10m 안이지만 20초에 약 130m씩 움직임
        sendPark(new double[][] { { 37.5338, 126.9104 }, { 37.5326, 126.9104 }, { 37.5314, 126.9104 }, { 37.5302, 126.9104 } }, 10);
        verify(tripService, never()).arriveStop(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 카페_앞을_걸어서_지나가면_도착_아님() {
        // 더현대 150m 안을 20초에 약 25m씩 지나감 (1분에 약 75m)
        send(new double[][] { { 37.5258, 126.9280 }, { 37.5258, 126.92828 }, { 37.5258, 126.92856 }, { 37.5258, 126.92884 } });
        verify(tripService, never()).arriveStop(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 카페에_멈춰_있으면_도착() {
        // 더현대 앞에서 GPS만 몇 m 흔들림
        send(new double[][] { { 37.52580, 126.92840 }, { 37.52583, 126.92842 }, { 37.52578, 126.92838 }, { 37.52581, 126.92841 } });
        verify(tripService).arriveStop(MEMBER_ID, TRIP_ID, 3L);
    }

    @Test
    void 공원에_있다가_스타벅스에_멈추면_스타벅스_도착_공원_출발() {
        trip.getStops().get(0).setArrivedAt("2026-11-06T13:00:00"); // 지금 칸 = 공원
        // 스타벅스 앞에 멈춤. 공원 경계 안이기도 함(0m)
        sendPark(new double[][] { { 37.52893, 126.934541 }, { 37.52895, 126.93455 }, { 37.52891, 126.93453 }, { 37.52893, 126.93454 } }, 0);
        verify(tripService).departStop(MEMBER_ID, TRIP_ID, 1L);
        verify(tripService).arriveStop(MEMBER_ID, TRIP_ID, 2L);
    }

    @Test
    void 공원_안을_걸어_다니면_공원_그대로() {
        trip.getStops().get(0).setArrivedAt("2026-11-06T13:00:00");
        sendPark(new double[][] { { 37.5338, 126.9104 }, { 37.53358, 126.9104 }, { 37.53336, 126.9104 }, { 37.53314, 126.9104 } }, 0);
        verify(tripService, never()).departStop(anyLong(), anyLong(), anyLong());
        verify(tripService, never()).arriveStop(anyLong(), anyLong(), anyLong());
    }

    @Test
    void 공원_경계_80m_밖으로_두_번_나가면_공원_출발() {
        trip.getStops().get(0).setArrivedAt("2026-11-06T13:00:00");
        sendPark(new double[][] { { 37.5350, 126.9090 }, { 37.5349, 126.9091 } }, 120);
        verify(tripService).departStop(MEMBER_ID, TRIP_ID, 1L);
    }

    @Test
    void 일반_칸은_200m_밖_두_번이면_출발() {
        trip.getStops().get(2).setArrivedAt("2026-11-06T13:00:00"); // 지금 칸 = 더현대
        send(new double[][] { { 37.5285, 126.9284 }, { 37.5284, 126.9284 } }); // 약 300m 북쪽
        verify(tripService).departStop(MEMBER_ID, TRIP_ID, 3L);
    }

    @Test
    void 최근_점이_4개가_안_되면_도착_판정_안_함() {
        // GPS가 끊겼다 와서 2분 안 점이 3개뿐 (오래된 점은 DB에서 이미 빠짐)
        send(new double[][] { { 37.52580, 126.92840 }, { 37.52581, 126.92841 }, { 37.52580, 126.92840 } });
        verify(tripService, never()).arriveStop(anyLong(), anyLong(), anyLong());
    }

    // ---------------------------------------------------------------- 도우미

    /** 공원 경계까지 거리가 모두 parkMetersEach인 점들로 판정 */
    private void sendPark(double[][] newestFirst, double parkMetersEach) {
        for (double[] point : newestFirst) {
            parkMeters.put(point[0], parkMetersEach);
        }
        send(newestFirst);
    }

    /** 최근 점(새 것부터)을 돌려주게 하고 제일 새 점을 GPS로 보냄 */
    private void send(double[][] newestFirst) {
        List<TrackPoint> recent = new ArrayList<>();
        for (double[] point : newestFirst) {
            TrackPoint trackPoint = new TrackPoint();
            trackPoint.setLat(point[0]);
            trackPoint.setLng(point[1]);
            recent.add(trackPoint);
        }
        when(trackMapper.selectRecentTrackList(eq(TRIP_ID), anyInt(), anyInt(), anyInt())).thenReturn(recent);

        TrackRequest request = new TrackRequest();
        request.setLat(newestFirst[0][0]);
        request.setLng(newestFirst[0][1]);
        request.setAccuracy(10.0);
        trackService.createTrack(MEMBER_ID, TRIP_ID, request);
    }

    private Stop stop(long id, String name, double lat, double lng) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setTripId(TRIP_ID);
        stop.setDay(1);
        stop.setName(name);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setState("todo");
        return stop;
    }
}
