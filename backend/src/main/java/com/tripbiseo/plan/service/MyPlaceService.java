package com.tripbiseo.plan.service;

import java.util.List;

import com.tripbiseo.plan.dto.MyPlace;
import com.tripbiseo.plan.dto.MyPlaceRequest;

/** ③ 내 장소: 친구 소식·장소 상세에서 저장한 곳 (식당 · 카페 · 놀거리로 나눠 보여 줌) */
public interface MyPlaceService {

    List<MyPlace> getMyPlaceList(long memberId);

    /** 저장하고 그 장소를 돌려줘요. 이미 저장한 곳이면 그대로 돌려줘요 */
    MyPlace saveMyPlace(long memberId, MyPlaceRequest request);

    void deleteMyPlace(long memberId, String placeId);
}
