package com.tripbiseo.trip.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.trip.dto.ChatMessage;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

/**
 * ② 여행·일정 (trip, trip_stop, trip_chat). SQL은 resources/mapper/trip/TripMapper.xml
 * 메서드 이름 = XML의 id. 이름은 SQL 그대로 select / insert / update / delete로 시작합니다.
 */
@Mapper
public interface TripMapper {

    // ---------------------------------------------------------------- 여행

    /** 회원의 여행 목록 (일정 칸 수 포함, 시작 날짜 최신순 → id 큰 순) */
    List<Trip> selectTripList(long memberId);

    /** 회원의 여행 하나 (없거나 남의 여행이면 null). @Param: 파라미터가 두 개라 XML에서 부를 이름을 정해 줌 */
    Trip selectTrip(@Param("tripId") long tripId, @Param("memberId") long memberId);

    /** 회원의 진행 중인 다른 여행 하나 (없으면 null) */
    Trip selectOtherOngoingTrip(@Param("memberId") long memberId, @Param("tripId") long tripId);

    /** 여행 저장. 저장 후 trip.id에 새 번호가 들어감 */
    int insertTrip(Trip trip);

    /** 여행 정보(이름·날짜·인원·수단) 수정 */
    int updateTrip(Trip trip);

    /** 여행 상태 바꾸기 (ongoing이면 started_at, done이면 finished_at도 기록) */
    int updateTripStatus(@Param("tripId") long tripId, @Param("status") String status);

    /** 여행 삭제 (일정·GPS·대화는 FK CASCADE로 같이 지워짐). 지운 줄 수 */
    int deleteTrip(@Param("tripId") long tripId, @Param("memberId") long memberId);

    // ---------------------------------------------------------------- 일정 칸

    /** 여행의 일정 칸 목록 (날짜 → 시작 시각 → id 순) */
    List<Stop> selectStopList(long tripId);

    /** 일정 칸 하나 (없으면 null) */
    Stop selectStop(@Param("tripId") long tripId, @Param("stopId") long stopId);

    /** 일정 칸 저장. 저장 후 stop.id에 새 번호가 들어감 */
    int insertStop(Stop stop);

    /** 일정 칸 수정 (day·time·stay·fixed·mode·memo·state) */
    int updateStop(Stop stop);

    /** 일정 칸 삭제. 지운 줄 수 */
    int deleteStop(@Param("tripId") long tripId, @Param("stopId") long stopId);

    /** 여러 일정 칸의 상태를 한 번에 바꾸기 (예: skipped) */
    int updateStopState(@Param("tripId") long tripId, @Param("stopIds") List<Long> stopIds, @Param("state") String state);

    /** GPS 도착 기록 (arrived_at = 지금). 이미 기록돼 있으면 그대로 */
    int updateStopArrived(@Param("tripId") long tripId, @Param("stopId") long stopId);

    /** GPS 출발 기록 (departed_at = 지금, state = done). 이미 기록돼 있으면 그대로 */
    int updateStopDeparted(@Param("tripId") long tripId, @Param("stopId") long stopId);

    // ---------------------------------------------------------------- 대화 기록

    /** 여행의 대화 기록 (시간순) */
    List<ChatMessage> selectChatList(long tripId);

    /** 대화 한 줄 저장. at이 null이면 지금 시각 */
    int insertChat(@Param("tripId") long tripId, @Param("chat") ChatMessage chat);
}
