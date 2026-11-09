package com.tripbiseo.common.util;

/**
 * 시연 계정 고정 이동 시간 (③ 11/08): 발표 당일 길찾기 API 하루 한도가 차도 시연 숫자가 바뀌지 않게,
 * 시나리오 구간은 한 번 실제로 받은 길찾기 값을 여기 적어 두고, 시연 계정에서 길찾기가 실패했을 때만 씀 (평소엔 실시간).
 * 다른 계정은 지금처럼 실시간 길찾기. 어림값(거리로 계산한 값)은 적지 않음
 */
public class DemoRoutes {

    private static final double SAME_PLACE_M = 200; // 이 거리 안이면 같은 장소로 봄

    // { 출발 위도, 출발 경도, 도착 위도, 도착 경도, 자동차(택시) 분, 대중교통 분 } · 값이 없으면 -1 (실시간 길찾기)
    private static final double[][] ROUTES = {
        // 영등포 타임스퀘어 → 서울역: 자동차 23분 = TMAP 자동차 길찾기 실제 값 (11/08 17:44 시연 시계)
        //   대중교통은 11/08 TMAP·ODsay 하루 한도가 차서 못 받음 → 한도가 풀리면 실제 값을 받아 채우기 (TODO)
        //   11/09 ① 상재: 대본 숫자(18:22 출발 → 지하철 19:02·KTX 놓침 / 택시 18:45·여유 15분)에 맞추려고 대중교통 40분을 적고, 시연 계정은 늘 이 값을 씀
        //   (늦음 시각 18:22는 DemoDrive.vue TRAIN_INCIDENT_CLOCK)
        { 37.5172094, 126.9040371, 37.55406888733184, 126.97070335253385, 23, 40 },
    };

    private DemoRoutes() {
    }

    /** 시나리오 구간의 고정 이동 분. 없으면 null. mode: walk / transit / car */
    public static Integer findMinutes(double lat1, double lng1, double lat2, double lng2, String mode) {
        for (double[] route : ROUTES) {
            boolean isSameFrom = Geo.distanceMeters(lat1, lng1, route[0], route[1]) < SAME_PLACE_M;
            boolean isSameTo = Geo.distanceMeters(lat2, lng2, route[2], route[3]) < SAME_PLACE_M;
            if (!isSameFrom || !isSameTo) {
                continue;
            }
            double minutes = "car".equals(mode) ? route[4] : "transit".equals(mode) ? route[5] : -1;
            return minutes < 0 ? null : (int) minutes;
        }
        return null;
    }
}
