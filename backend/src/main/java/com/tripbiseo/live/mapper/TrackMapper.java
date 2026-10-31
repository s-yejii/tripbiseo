package com.tripbiseo.live.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ④ GPS 기록 (trip_track). SQL은 resources/mapper/live/TrackMapper.xml
 * 예전에는 GPS SQL이 TripMapper(②)에 있었는데, 테이블 주인(④)의 Mapper로 옮겼어요.
 */
@Mapper
public interface TrackMapper {

    /** GPS 한 점 저장. accuracy는 선택이라 null 가능 (그래서 Double) */
    int insertTrack(@Param("tripId") long tripId, @Param("lat") double lat, @Param("lng") double lng,
                    @Param("accuracy") Double accuracy);

    // TODO: ④ 아래를 만들기 (참고: backend-v0 태그 resources/mapper/TripMapper.xml의 lastTrack·findTracks)
    //   TrackPoint selectLastTrack(long tripId);      — 바로 앞 GPS 한 점 (출발 두 번 연속 확인)
    //   List<TrackPoint> selectTrackList(long tripId); — 회고 GPS 선
    //   int deleteTrackList(long memberId);           — 위치 기록 모두 지우기 (trip JOIN으로 내 여행만)
}
