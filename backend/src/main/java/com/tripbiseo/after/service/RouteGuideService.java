package com.tripbiseo.after.service;

import com.tripbiseo.common.external.RouteGuide;

/** ⑤ 길 안내: 지금 위치에서 다음 장소까지 경로선 + 단계별 안내 */
public interface RouteGuideService {

    /** 길 안내 (mode: walk / bus / subway / car). 좌표·수단이 이상하면 400, 그 수단으로 못 찾으면 available=false */
    RouteGuide getGuide(Double fromLat, Double fromLng, Double toLat, Double toLng, String mode);
}
