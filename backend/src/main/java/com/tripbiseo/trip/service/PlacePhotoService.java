package com.tripbiseo.trip.service;

import java.util.List;

import com.tripbiseo.trip.dto.PlacePhoto;
import com.tripbiseo.trip.dto.TripPhotoRequest;

/** ② 장소 사진 찾기 */
public interface PlacePhotoService {

    /** 장소들의 사진. DB 사진 먼저, 없으면 카카오 블로그 검색에서 제목에 가게 이름이 있는 글의 사진. 없는 곳은 목록에서 빠짐 */
    List<PlacePhoto> getPlacePhotoList(List<String> placeIds);

    /** 한 일정의 장소들 사진: 카카오 이미지 검색으로, 같은 일정 안에서 같은 사진은 안 씀 (찾은 곳만) */
    List<PlacePhoto> getTripPhotoList(List<TripPhotoRequest> stops);
}
