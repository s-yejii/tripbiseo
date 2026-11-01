package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** 바로 앞 GPS 한 점 (도착·출발 두 번 연속 확인용). 첫 점이면 null */
    TrackPoint selectLastTrack(long tripId);

    /** 여행의 GPS 점 전부, 시간순 (⑤ 회고 지도 GPS 선) */
    List<TrackPoint> selectTrackList(long tripId);

    // TODO: ④ int deleteTrackList(long memberId); — 위치 기록 모두 지우기 (trip JOIN으로 내 여행만)
}
