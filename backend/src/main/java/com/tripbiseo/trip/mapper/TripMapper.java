package com.tripbiseo.trip.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

/**
 * ② 여행·일정 (trip, trip_stop, trip_chat). SQL은 resources/mapper/trip/TripMapper.xml
 * 메서드 이름 = XML의 id. 이름은 SQL 그대로 select / insert / update / delete로 시작합니다.
 */
@Mapper
public interface TripMapper {

    /** [완성 예시] 회원의 여행 목록 (일정 칸 수 포함, 시작 날짜 최신순 → id 큰 순) */
    List<Trip> selectTripList(long memberId);

    /** 회원의 여행 하나 (없거나 남의 여행이면 null). @Param: 파라미터가 두 개라 XML에서 부를 이름을 정해 줌 */
    Trip selectTrip(@Param("tripId") long tripId, @Param("memberId") long memberId);

    /** 여행의 일정 칸 목록 (날짜 → 시작 시각 → id 순) */
    List<Stop> selectStopList(long tripId);

    // TODO: ② 아래처럼 하나씩 추가 (참고: backend-v0 태그의 resources/mapper/TripMapper.xml)
    //   int insertTrip(Trip trip);  int insertStop(Stop stop);  int updateStop(Stop stop); …
}
