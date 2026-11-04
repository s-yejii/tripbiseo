package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tripbiseo.live.dto.PlaceTagCount;

/**
 * ④ 추천용 리뷰 태그 세기. retrip_review는 공통 테이블이라 읽기(SELECT)만 해요.
 * SQL은 resources/mapper/live/PlaceTagMapper.xml
 */
@Mapper
public interface PlaceTagMapper {

    /** 후보 장소들의 리뷰 태그 개수 (후보만 세서 빠름. 반경으로 100곳 이하로 줄인 뒤 부름) */
    List<PlaceTagCount> selectPlaceTagCountList(List<String> placeIds);
}
