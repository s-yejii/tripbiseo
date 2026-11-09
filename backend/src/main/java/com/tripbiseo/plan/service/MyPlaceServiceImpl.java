package com.tripbiseo.plan.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.plan.dto.MyPlace;
import com.tripbiseo.plan.dto.MyPlaceRequest;
import com.tripbiseo.plan.mapper.MyPlaceMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MyPlaceServiceImpl implements MyPlaceService {

    private static final int MAX_PLACES = 200; // 한 사람이 저장할 수 있는 곳 수

    private final MyPlaceMapper myPlaceMapper;

    @Override
    public List<MyPlace> getMyPlaceList(long memberId) {
        return myPlaceMapper.selectMyPlaceList(memberId);
    }

    @Override
    public MyPlace saveMyPlace(long memberId, MyPlaceRequest request) {
        String placeId = findPlaceId(request);
        List<MyPlace> list = myPlaceMapper.selectMyPlaceList(memberId);
        MyPlace saved = findIn(list, placeId);
        if (saved != null) {
            return saved; // 이미 저장한 곳
        }
        if (list.size() >= MAX_PLACES) {
            throw fail(400, "내 장소는 " + MAX_PLACES + "곳까지 저장할 수 있어요.");
        }
        myPlaceMapper.insertMyPlace(memberId, placeId, blankToNull(request.getFromName()));
        return findIn(myPlaceMapper.selectMyPlaceList(memberId), placeId);
    }

    @Override
    public void deleteMyPlace(long memberId, String placeId) {
        myPlaceMapper.deleteMyPlace(memberId, placeId);
    }

    // placeId가 있으면 그걸, 없으면 이름으로 찾기. 둘 다 없거나 우리 DB에 없으면 404
    private String findPlaceId(MyPlaceRequest request) {
        String placeId = blankToNull(request.getPlaceId());
        if (placeId != null) {
            if (myPlaceMapper.countPlace(placeId) == 0) {
                throw fail(404, "장소를 찾지 못했어요.");
            }
            return placeId;
        }
        String name = blankToNull(request.getName());
        if (name == null) {
            throw fail(400, "저장할 장소를 골라 주세요.");
        }
        String found = myPlaceMapper.selectPlaceIdByName(name.trim());
        if (found == null) {
            throw fail(404, name + "을(를) 찾지 못했어요.");
        }
        return found;
    }

    private MyPlace findIn(List<MyPlace> list, String placeId) {
        for (MyPlace place : list) {
            if (place.getPlaceId().equals(placeId)) {
                return place;
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
