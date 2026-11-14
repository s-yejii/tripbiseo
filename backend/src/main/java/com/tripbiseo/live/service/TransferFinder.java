package com.tripbiseo.live.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tripbiseo.common.external.GuideStep;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.external.RouteGuideClient;
import com.tripbiseo.common.util.Geo;

import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * ④ 돌발 ③ (11/06): 고정 일정(KTX)에 늦을 때 "지하철로 ○○역까지 간 뒤 택시"처럼 더 빨리 가는 방법 찾기.
 * 숫자는 모두 실제 길찾기(TMAP 자동차, ODsay 대중교통)로 계산하고, 대중교통 그대로보다 빠를 때만 알려 줘요.
 * AlternativeServiceImpl·StatusServiceImpl(놓침 판단, 11/12)에서만 쓰는 도우미라 인터페이스 없이 @Component (LiveWeatherChecker와 같은 방식).
 */
@Component
@RequiredArgsConstructor
public class TransferFinder {

    private static final List<String> TRANSIT = List.of("subway", "bus", "walk");
    private static final String GETOFF_SUFFIX = "에서 내리기"; // RouteGuideClient의 내리는 곳 안내 끝말

    private final RouteClient routeClient;           // 구간 시간 (TMAP·ODsay, 공통)
    private final RouteGuideClient routeGuideClient; // 지하철 노선·내리는 역 (공통)

    /** 찾은 방법 한 가지 */
    @Data
    public static class Option {
        private String text;   // "지하철로 공덕역까지 18분 → 택시 9분"
        private String title;  // "공덕역에서 택시로 갈아타기"
        private int minutes;   // 전체 이동 분
        private int transitMinutes; // 비교한 대중교통 그대로의 분
    }

    /**
     * 출발점 → 도착 칸까지 가장 빠른 방법 (대중교통 그대로보다 빠를 때만, 아니면 null).
     * 후보: 택시로 바로 / 지하철로 갈아타는 역(마지막 내리는 역 빼고)까지 간 뒤 택시
     */
    public Option findFaster(double fromLat, double fromLng, double toLat, double toLng) {
        int transit = minutesOf(fromLat, fromLng, toLat, toLng, "transit");
        Option best = new Option();
        best.setTitle("택시로 바로 가기");
        best.setMinutes(minutesOf(fromLat, fromLng, toLat, toLng, "taxi"));
        best.setText("택시 " + Geo.toDuration(best.getMinutes()));

        List<GuideStep> steps;
        try {
            steps = routeGuideClient.getGuide(fromLat, fromLng, toLat, toLng, "subway").getSteps();
        } catch (RuntimeException e) {
            steps = List.of(); // 지하철 안내를 못 받으면(키 없음·느린 인터넷) "택시로 바로 가기"만 비교 (11/08: 발표장에서 ③ 대안이 안 나오는 일 없게)
        }
        int lastGetoff = findLastGetoff(steps);
        for (int i = 0; i < lastGetoff; i++) {
            GuideStep step = steps.get(i);
            if (!"getoff".equals(step.getKind()) || step.getLat() == null) {
                continue;
            }
            Option option = tryTransferAt(step, fromLat, fromLng, toLat, toLng);
            if (option.getMinutes() < best.getMinutes()) {
                best = option;
            }
        }
        best.setTransitMinutes(transit);
        return best.getMinutes() < transit ? best : null;
    }

    /** 출발점 → 도착 칸까지 가장 빠른 방법의 분: 택시·갈아타기가 더 빠르면 그 분, 아니면 대중교통 분 (놓침 판단, 11/12 ④) */
    public int findFastestMinutes(double fromLat, double fromLng, double toLat, double toLng) {
        Option faster = findFaster(fromLat, fromLng, toLat, toLng);
        if (faster != null) {
            return faster.getMinutes();
        }
        return minutesOf(fromLat, fromLng, toLat, toLng, "transit"); // findFaster가 null이면 대중교통이 가장 빠름
    }

    /** 이 역까지 대중교통 + 거기서 택시 */
    private Option tryTransferAt(GuideStep getoff, double fromLat, double fromLng, double toLat, double toLng) {
        String station = getoff.getText().replace(GETOFF_SUFFIX, "");
        int toStation = minutesOf(fromLat, fromLng, getoff.getLat(), getoff.getLng(), "transit");
        int byTaxi = minutesOf(getoff.getLat(), getoff.getLng(), toLat, toLng, "taxi");
        Option option = new Option();
        option.setTitle(station + "에서 택시로 갈아타기");
        option.setText("지하철로 " + station + "까지 " + Geo.toDuration(toStation) + " → 택시 " + Geo.toDuration(byTaxi));
        option.setMinutes(toStation + byTaxi);
        return option;
    }

    /** 마지막 "내리기" 안내의 위치 (없으면 0이라 후보가 없음) */
    private int findLastGetoff(List<GuideStep> steps) {
        int last = 0;
        for (int i = 0; i < steps.size(); i++) {
            if ("getoff".equals(steps.get(i).getKind())) {
                last = i;
            }
        }
        return last;
    }

    /** 실제 길찾기 분 (mode: transit / taxi) */
    private int minutesOf(double lat1, double lng1, double lat2, double lng2, String mode) {
        return routeClient.getRoute(lat1, lng1, lat2, lng2, mode, TRANSIT, true).getMinutes();
    }
}
