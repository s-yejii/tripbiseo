package com.tripbiseo.plan.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.CrowdHour;

/**
 * ③ 검증 줄의 붐빔 (11/13). crowd_cell·crowd_hour는 ② 찬기 표라 읽기(SELECT)만 해요 (찬기 OK, 찬기와 같은 쿼리).
 * SQL은 resources/mapper/plan/PlanCrowdMapper.xml
 */
@Mapper
public interface PlanCrowdMapper {

    /** 좌표가 든 250m 칸의 그 요일(1=월 … 7=일, 8=공휴일)·시각 평소 붐빔. 칸·시각 데이터가 없으면 null */
    CrowdHour selectCrowdHour(@Param("lat") double lat, @Param("lng") double lng, @Param("dayKind") int dayKind, @Param("hour") int hour);
}
