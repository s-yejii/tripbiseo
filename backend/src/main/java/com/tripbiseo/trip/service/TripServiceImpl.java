package com.tripbiseo.trip.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.ChatMessage;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/**
 * ② 여행·일정. getTripList만 진짜(완성 예시)이고, 나머지는 화면이 그려지게 가짜 값을 돌려줘요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/TripService.java
 * 공통 주의: 회원 번호는 Controller가 LoginUser.id()로 꺼내 넘김(요청마다 member 다시 조회 안 함),
 *           남의 테이블은 SELECT만, 입력 검사 오류 문장은 docs/API.md 그대로
 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private final TripMapper tripMapper;

    /** [완성 예시] 내 여행 목록: Mapper가 DB에서 읽은 그대로 돌려줌 (stops는 DTO 기본값 []) */
    @Override
    public List<Trip> getTripList(long memberId) {
        return tripMapper.selectTripList(memberId);
    }

    /** 여행 + 일정 칸 */
    // TODO: ② 여행 상세. selectTrip(tripId, memberId) → 없으면 404 "여행을 찾을 수 없어요." → selectStopList로 stops 채우기. 참고: 예전 TripService.get
    @Override
    public Trip getTrip(long memberId, long tripId) {
        return sampleTrip(memberId, tripId, "planned");
    }

    /** 여행을 일정 칸까지 한 번에 저장 */
    // TODO: ② 여행 저장. 입력 검사(API.md 오류 문장) → insertTrip → 칸마다 검사 + insertStop. 참고: 예전 TripService.create/checkInfo/checkStop
    //       주의: DB에 여러 번 쓰니 @Transactional 유지. placeId가 DB에 없으면 null로 (PlaceService.getPlace로 확인)
    @Override
    @Transactional
    public Trip createTrip(long memberId, Trip trip) {
        trip.setId(12L);
        trip.setMemberId(memberId);
        trip.setStatus("planned");
        return trip;
    }

    /** 여행 정보만 수정 */
    // TODO: ② 여행 수정. title·startDate·endDate·people·transports만 읽기, 줄인 날짜 밖에 일정이 있으면 400. 참고: 예전 TripService.update
    @Override
    public Trip updateTrip(long memberId, long tripId, Trip trip) {
        Trip saved = sampleTrip(memberId, tripId, "planned");
        saved.setTitle(trip.getTitle());
        return saved;
    }

    /** 여행 삭제 */
    // TODO: ② 여행 삭제. deleteTrip(tripId, memberId)가 0줄이면 404 (일정·GPS·대화는 FK ON DELETE CASCADE로 같이 지워짐). 참고: 예전 TripService.delete
    @Override
    public void deleteTrip(long memberId, long tripId) {
    }

    /** 여행 시작 */
    // TODO: ② 여행 시작. 오늘이 기간 밖이면 400, 진행 중인 다른 여행이 있으면 409 → updateStatus ongoing. 참고: 예전 TripService.start
    @Override
    public Trip startTrip(long memberId, long tripId) {
        return sampleTrip(memberId, tripId, "ongoing");
    }

    /** 여행 마치기 */
    // TODO: ② 여행 마치기. 진행 중이 아니어도 막지 않음 → updateStatus done. 참고: 예전 TripService.finish
    @Override
    public Trip finishTrip(long memberId, long tripId) {
        return sampleTrip(memberId, tripId, "done");
    }

    /** 일정 칸 하나 추가 → 여행 전체 */
    // TODO: ② 일정 칸 추가. 칸 검사 → insertStop(state는 항상 todo) → getTrip. 참고: 예전 TripService.addStop/checkStop
    @Override
    public Trip createStop(long memberId, long tripId, Stop stop) {
        Trip trip = sampleTrip(memberId, tripId, "planned");
        stop.setId(104L);
        stop.setTripId(tripId);
        stop.setState("todo");
        trip.getStops().add(stop);
        return trip;
    }

    /** 일정 칸 수정 → 여행 전체 */
    // TODO: ② 일정 칸 수정. day·time·stay·fixed·state는 null이면 원래 값, mode·memo는 항상 덮어씀(API.md 주의) → updateStop. 참고: 예전 TripService.updateStop
    @Override
    public Trip updateStop(long memberId, long tripId, long stopId, Stop stop) {
        return sampleTrip(memberId, tripId, "planned");
    }

    /** 일정 칸 삭제 → 여행 전체 */
    // TODO: ② 일정 칸 삭제. deleteStop이 0줄이면 404 "일정을 찾을 수 없어요." 참고: 예전 TripService.deleteStop
    @Override
    public Trip deleteStop(long memberId, long tripId, long stopId) {
        return sampleTrip(memberId, tripId, "planned");
    }

    /** 여러 일정 칸을 skipped로 */
    // TODO: ② 일정 칸 빼기. 내 여행인지 확인 → updateStopState(stopIds, 'skipped'). ④ 대안 적용이 부름
    @Override
    @Transactional
    public void skipStops(long memberId, long tripId, List<Long> stopIds) {
    }

    /** 이 여행의 대화 기록 */
    // TODO: ② 대화 기록 조회. 내 여행인지 확인 → selectChatList (at은 yyyy-MM-ddTHH:mm). 참고: 예전 TripService.chats, TripMapper.xml findChats
    @Override
    public List<ChatMessage> getChatList(long memberId, long tripId) {
        List<ChatMessage> chats = new ArrayList<>();
        chats.add(chat("me", "근처 카페 추천해 줘", "2026-10-20T14:02"));
        chats.add(chat("ai", "반경 몇 m 안에서 찾을까요?", "2026-10-20T14:02"));
        return chats;
    }

    /** 대화 기록 여러 줄 저장 */
    // TODO: ② 대화 기록 저장. role이 me/ai가 아니거나 text가 비면 건너뜀, 2000자로 자르기, at 형식이 틀리면 지금 시각.
    //       참고: 예전 TripService.addChats. 주의: 예전 PlanService.str 같은 static 도우미는 쓰지 말고 이 클래스 private으로
    @Override
    @Transactional
    public void createChats(long memberId, long tripId, List<ChatMessage> messages) {
    }

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static Trip sampleTrip(long memberId, long tripId, String status) {
        String today = Geo.now().toLocalDate().toString();
        Trip trip = new Trip();
        trip.setId(tripId);
        trip.setMemberId(memberId);
        trip.setTitle("서울 하루");
        trip.setStartDate(today);
        trip.setEndDate(today);
        trip.setPeople(2);
        trip.setTransports(new ArrayList<>(List.of("subway", "bus", "walk")));
        trip.setStatus(status);
        trip.getStops().add(sampleStop(101L, tripId, "e70caff5-27cf-5c76-a1f3-201b7032d672", "경복궁", "관광지",
                "서울 종로구 사직로 161", 37.5796, 126.977, "10:00", 90, false));
        trip.getStops().add(sampleStop(102L, tripId, null, "카페OO", "카페", null, 37.5745, 126.9849, "13:30", 55, false));
        trip.getStops().add(sampleStop(103L, tripId, null, "세종문화회관", "문화시설", "서울 종로구 세종대로 175",
                37.5725, 126.9759, "19:00", 120, true));
        return trip;
    }

    private static Stop sampleStop(long id, long tripId, String placeId, String name, String category, String address,
                                   double lat, double lng, String time, int stay, boolean isFixed) {
        Stop stop = new Stop();
        stop.setId(id);
        stop.setTripId(tripId);
        stop.setDay(1);
        stop.setPlaceId(placeId);
        stop.setName(name);
        stop.setCategory(category);
        stop.setAddress(address);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setMemo("");
        stop.setState("todo");
        return stop;
    }

    private static ChatMessage chat(String role, String text, String at) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setText(text);
        message.setAt(at);
        return message;
    }
}
