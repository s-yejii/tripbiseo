package com.tripbiseo.trip.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tripbiseo.trip.dto.Trip;

/**
 * ② 여행·일정 (trip, trip_stop, trip_chat). SQL은 resources/mapper/trip/TripMapper.xml
 * 메서드 이름 = XML의 id. 이름은 SQL 그대로 select / insert / update / delete로 시작합니다.
 */
@Mapper
public interface TripMapper {

    /** [완성 예시] 회원의 여행 목록 (일정 칸 수 포함, 시작 날짜 최신순 → id 큰 순) */
    List<Trip> selectTripList(long memberId);

    // TODO: ② 아래처럼 하나씩 추가 (참고: backend-v0 태그의 resources/mapper/TripMapper.xml)
    //   Trip selectTrip(@Param("tripId") long tripId, @Param("memberId") long memberId);
    //   List<Stop> selectStopList(long tripId);
    //   int insertTrip(Trip trip);  int insertStop(Stop stop);  int updateStop(Stop stop); …
}
