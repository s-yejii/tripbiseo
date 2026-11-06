package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.live.dto.StopAreaMeters;
import com.tripbiseo.live.dto.TrackPoint;

/**
 * ④ GPS 기록 (trip_track). SQL은 resources/mapper/live/TrackMapper.xml
 * 예전에는 GPS SQL이 TripMapper(②)에 있었는데, 테이블 주인(④)의 Mapper로 옮겼어요.
 */
@Mapper
public interface TrackMapper {

    /** GPS 한 점 저장. accuracy는 선택이라 null 가능 (그래서 Double) */
    int insertTrack(@Param("tripId") long tripId, @Param("lat") double lat, @Param("lng") double lng,
                    @Param("accuracy") Double accuracy);

    /** 최근 GPS 점, 새 것부터 최대 count개 (도착·출발 판정용). 오차가 maxAccuracy보다 큰 점, seconds초보다 오래된 점은 뺌 */
    List<TrackPoint> selectRecentTrackList(@Param("tripId") long tripId, @Param("count") int count,
                                           @Param("seconds") int seconds, @Param("maxAccuracy") int maxAccuracy);

    /** 이 점에서 경계가 있는 칸(place_area와 이어진 칸)까지 거리. 경계 있는 칸만 나옴 */
    List<StopAreaMeters> selectAreaMetersList(@Param("tripId") long tripId, @Param("lat") double lat, @Param("lng") double lng);

    /** 여행의 GPS 점 전부, 시간순 (⑤ 회고 지도 GPS 선) */
    List<TrackPoint> selectTrackList(long tripId);

    /** 이 회원의 모든 여행 GPS 기록 지우기 (① 위치 기록 지우기). 지운 줄 수를 돌려줌 */
    int deleteTrackList(long memberId);
}
