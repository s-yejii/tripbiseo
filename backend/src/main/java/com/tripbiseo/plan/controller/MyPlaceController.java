package com.tripbiseo.plan.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.plan.dto.MyPlace;
import com.tripbiseo.plan.dto.MyPlaceRequest;
import com.tripbiseo.plan.service.MyPlaceService;

import lombok.RequiredArgsConstructor;

/** ③ 내 장소 (마이 > 내 장소, 친구 소식 "저장"). 로그인 필요 */
@RestController
@RequestMapping("/api/my/places")
@RequiredArgsConstructor
public class MyPlaceController {

    private final MyPlaceService myPlaceService;
    private final LoginUser loginUser;

    /** 내 장소 전부 (최근 저장 순). group으로 식당 · 카페 · 놀거리를 나눠요 */
    @GetMapping
    public List<MyPlace> getMyPlaceList() {
        return myPlaceService.getMyPlaceList(loginUser.id());
    }

    /** 저장. { placeId } 또는 { name, fromName } */
    @PostMapping
    public MyPlace saveMyPlace(@RequestBody MyPlaceRequest request) {
        return myPlaceService.saveMyPlace(loginUser.id(), request);
    }

    /** 지우기 */
    @DeleteMapping("/{placeId}")
    public OkResponse deleteMyPlace(@PathVariable String placeId) {
        myPlaceService.deleteMyPlace(loginUser.id(), placeId);
        return new OkResponse();
    }
}
