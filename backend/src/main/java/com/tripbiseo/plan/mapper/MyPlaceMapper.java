package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.MyPlace;

/** ③ 내 장소 (my_place). retrip_place는 공통 테이블이라 읽기만 해요. SQL은 resources/mapper/plan/MyPlaceMapper.xml */
@Mapper
public interface MyPlaceMapper {

    /** 내 장소 전부, 최근 저장 순 */
    List<MyPlace> selectMyPlaceList(@Param("memberId") long memberId);

    /** 이름이 똑같은 장소 중 리뷰가 가장 많은 곳의 id (없으면 null) */
    String selectPlaceIdByName(@Param("name") String name);

    /** 장소가 우리 DB에 있는지 */
    int countPlace(@Param("placeId") String placeId);

    /** 저장. 이미 있으면 아무것도 안 하고 0을 돌려줘요 */
    int insertMyPlace(@Param("memberId") long memberId, @Param("placeId") String placeId, @Param("fromName") String fromName);

    /** 지우기. 내 것만 지워져요 */
    int deleteMyPlace(@Param("memberId") long memberId, @Param("placeId") String placeId);
}
