package com.tripbiseo.trip.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.trip.dto.ChatMessage;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ② 여행·일정·대화 기록 API. Controller는 요청을 받아 Service에 넘기기만 해요 (로직은 TripServiceImpl).
 * 회원 번호는 loginUser.id()로 꺼내서 넘겨요.
 */
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final LoginUser loginUser;

    /** [완성 예시] 내 여행 목록 */
    @GetMapping
    public List<Trip> getTripList() {
        return tripService.getTripList(loginUser.id());
    }

    /** 여행 저장 (일정 칸까지 한 번에) */
    @PostMapping
    public Trip createTrip(@RequestBody Trip trip) {
        return tripService.createTrip(loginUser.id(), trip);
    }

    /** 여행 상세 */
    @GetMapping("/{id}")
    public Trip getTrip(@PathVariable long id) {
        return tripService.getTrip(loginUser.id(), id);
    }

    /** 여행 정보 수정 (이름·날짜·인원·수단) */
    @PutMapping("/{id}")
    public Trip updateTrip(@PathVariable long id, @RequestBody Trip trip) {
        return tripService.updateTrip(loginUser.id(), id, trip);
    }

    /** 여행 삭제 */
    @DeleteMapping("/{id}")
    public OkResponse deleteTrip(@PathVariable long id) {
        tripService.deleteTrip(loginUser.id(), id);
        return new OkResponse();
    }

    /** 여행 시작 */
    @PostMapping("/{id}/start")
    public Trip startTrip(@PathVariable long id) {
        return tripService.startTrip(loginUser.id(), id);
    }

    /** 여행 마치기 */
    @PostMapping("/{id}/finish")
    public Trip finishTrip(@PathVariable long id) {
        return tripService.finishTrip(loginUser.id(), id);
    }

    /** 일정 칸 추가 */
    @PostMapping("/{id}/stops")
    public Trip createStop(@PathVariable long id, @RequestBody Stop stop) {
        return tripService.createStop(loginUser.id(), id, stop);
    }

    /** 일정 칸 수정 */
    @PutMapping("/{id}/stops/{stopId}")
    public Trip updateStop(@PathVariable long id, @PathVariable long stopId, @RequestBody Stop stop) {
        return tripService.updateStop(loginUser.id(), id, stopId, stop);
    }

    /** 일정 칸 삭제 */
    @DeleteMapping("/{id}/stops/{stopId}")
    public Trip deleteStop(@PathVariable long id, @PathVariable long stopId) {
        return tripService.deleteStop(loginUser.id(), id, stopId);
    }

    /** 이 여행의 대화 기록 */
    @GetMapping("/{id}/chats")
    public List<ChatMessage> getChatList(@PathVariable long id) {
        return tripService.getChatList(loginUser.id(), id);
    }

    /** 대화 기록 여러 줄 저장 (본문은 배열) */
    @PostMapping("/{id}/chats")
    public OkResponse createChats(@PathVariable long id, @RequestBody List<ChatMessage> messages) {
        tripService.createChats(loginUser.id(), id, messages);
        return new OkResponse();
    }
}
