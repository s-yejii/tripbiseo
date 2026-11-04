package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.AreaStat;
import com.tripbiseo.plan.dto.DongShape;
import com.tripbiseo.plan.dto.DongType;

/** ③ 추천 지역 세기. retrip_place는 공통 테이블이라 읽기(SELECT)만 해요. SQL은 resources/mapper/plan/AreaStatMapper.xml */
@Mapper
public interface AreaStatMapper {

    /** 서울 구별·종류별 장소 수와 리뷰 수. 종류마다 리뷰가 많은 순으로 limit개 */
    List<AreaStat> selectAreaStatList(@Param("limit") int limit);

    /** 경계(GeoJSON) 안의 장소, 리뷰 많은 순 */
    java.util.List<com.tripbiseo.trip.dto.Place> selectPlacesInShape(@Param("geometry") String geometry, @Param("limit") int limit);

    /** 서울 전체의 음식점·카페·볼거리 수 */
    DongType selectSeoulKindCount();

    /** 행정동마다 음식점·카페·볼거리 수 */
    List<DongType> selectDongKindCountList(@Param("dongs") List<DongShape> dongs);
}
