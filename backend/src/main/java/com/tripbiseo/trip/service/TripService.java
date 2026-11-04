package com.tripbiseo.trip.service;

import java.util.List;

import com.tripbiseo.trip.dto.ChatMessage;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;

/**
 * ② 여행·일정. 모든 메서드는 로그인한 회원(memberId)의 여행만 찾고, 남의 여행이면 404 "여행을 찾을 수 없어요."
 * ③④⑤가 부르는 공유 메서드: getTrip, createTrip, createStop, updateStop, skipStops, arriveStop, departStop, createChats (docs/ROLES.md)
 */
public interface TripService {

    /** 내 여행 목록 (stopCount 있음, stops는 []) */
    List<Trip> getTripList(long memberId);

    /** 여행 + 일정 칸 (③④⑤가 부름) */
    Trip getTrip(long memberId, long tripId);

    /** 여행을 일정 칸까지 한 번에 저장 (③ 초안 저장도 이것) */
    Trip createTrip(long memberId, Trip trip);

    /** 여행 정보(이름·날짜·인원·수단)만 수정 */
    Trip updateTrip(long memberId, long tripId, Trip trip);

    /** 여행 삭제 (일정·GPS·대화 기록도 DB에서 같이 지워짐) */
    void deleteTrip(long memberId, long tripId);

    /** 휴지통 목록 (최근에 지운 순) */
    List<Trip> getTrashList(long memberId);

    /** 휴지통에서 꺼내기: 원래 상태로 돌아가요. 진행 중이던 여행인데 지금 다른 여행이 진행 중이면 409 */
    Trip restoreTrip(long memberId, long tripId);

    /** 휴지통의 여행 영구 삭제 */
    void deleteTrashTrip(long memberId, long tripId);

    /** 휴지통 비우기 */
    void deleteAllTrash(long memberId);

    /** 여행 시작 (status → ongoing). 여행 기간에만, 진행 중인 다른 여행이 없을 때만 */
    Trip startTrip(long memberId, long tripId);

    /** 여행 마치기 (status → done) */
    Trip finishTrip(long memberId, long tripId);

    /** 진행 중인 여행을 보관함으로 (지우지 않고 진행 중에서만 빼기) */
    Trip archiveTrip(long memberId, long tripId);

    /** 일정 칸 하나 추가 → 여행 전체 (④ 추천 장소 넣기도 이것) */
    Trip createStop(long memberId, long tripId, Stop stop);

    /** 일정 칸 수정 → 여행 전체. mode·memo는 보낸 값으로 덮어씀 (④ 대안 적용도 이것) */
    Trip updateStop(long memberId, long tripId, long stopId, Stop stop);

    /** 일정 칸 삭제 → 여행 전체 */
    Trip deleteStop(long memberId, long tripId, long stopId);

    /** 일정 칸 전부를 한 번에 저장 → 여행 전체. 목록에 없는 칸은 지우고, 있는 칸은 고침 (편집 화면 "완료") */
    Trip updateStops(long memberId, long tripId, List<Stop> stops);

    /** 여러 일정 칸을 state = skipped로 (④ 대안 적용에서 빠지는 칸) */
    void skipStops(long memberId, long tripId, List<Long> stopIds);

    /** GPS 도착 기록: arrived_at = 지금 (④ GPS 판정이 부름). 이미 기록돼 있으면 그대로 */
    void arriveStop(long memberId, long tripId, long stopId);

    /** GPS 출발 기록: departed_at = 지금, state = done (④ GPS 판정이 부름). 이미 기록돼 있으면 그대로 */
    void departStop(long memberId, long tripId, long stopId);

    /** 이 여행의 대화 기록 (시간순) */
    List<ChatMessage> getChatList(long memberId, long tripId);

    /** 대화 기록 여러 줄 저장 (③ 계획 대화, ④ 여행 중 대화) */
    void createChats(long memberId, long tripId, List<ChatMessage> messages);
}
