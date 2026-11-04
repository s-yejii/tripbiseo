package com.tripbiseo.plan.service;

import java.util.List;

import com.tripbiseo.plan.dto.AreaStat;
import com.tripbiseo.plan.dto.DongShape;
import com.tripbiseo.plan.dto.DongType;

/** ③ 일정 만들기 추천 지역 */
public interface AreaStatService {

    /** 종류(카페·맛집·술집·관광)마다 리뷰가 많은 서울 구 목록 */
    List<AreaStat> getAreaStatList();

    /** 한 구의 행정동 특색: 구 평균보다 가장 두드러진 종류 (입지계수) */
    List<DongType> getDongTypeList(List<DongShape> dongs);
}
