package com.tripbiseo.after.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.external.RouteGuide;
import com.tripbiseo.common.external.RouteGuideClient;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RouteGuideServiceImpl implements RouteGuideService {

    private static final List<String> MODES = List.of("walk", "bus", "subway", "car");

    private final RouteGuideClient routeGuideClient;

    /** 길 안내 */
    @Override
    public RouteGuide getGuide(Double fromLat, Double fromLng, Double toLat, Double toLng, String mode) {
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
            throw fail(400, "출발·도착 좌표가 필요해요.");
        }
        if (mode == null || !MODES.contains(mode)) {
            throw fail(400, "이동 수단은 walk·bus·subway·car 중 하나예요.");
        }
        return routeGuideClient.getGuide(fromLat, fromLng, toLat, toLng, mode);
    }
}
