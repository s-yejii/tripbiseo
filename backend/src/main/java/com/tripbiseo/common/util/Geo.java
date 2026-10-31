package com.tripbiseo.common.util;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 거리·시간 계산 도우미.
 * 이미 손에 있는 두 점 사이 거리(GPS 한 점 ↔ 다음 장소 도착 판정 등)에만 씁니다.
 * DB에서 "반경 안·가까운 순"으로 찾는 건 SQL의 PostGIS(ST_DWithin, ORDER BY geom <-> 점)로 합니다.
 */
public final class Geo {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final double EARTH_RADIUS_M = 6371e3;
    private static final int MINUTES_PER_DAY = 1440;

    private Geo() {}

    /** 두 좌표 사이 거리(m) — 하버사인 공식 */
    public static double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        double toRadian = Math.PI / 180;
        double dLat = (lat2 - lat1) * toRadian;
        double dLng = (lng2 - lng1) * toRadian;
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(lat1 * toRadian) * Math.cos(lat2 * toRadian) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_M * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** "14:30" → 870 (자정부터 센 분). 형식이 틀리면 0 */
    public static int toMinutes(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) {
            return 0;
        }
        String[] parts = hhmm.split(":");
        return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
    }

    /** 870 → "14:30" (자정을 넘으면 24시간 안으로) */
    public static String toHhmm(int minutes) {
        int m = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY;
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    /** 지금 한국 시각 */
    public static ZonedDateTime now() {
        return ZonedDateTime.now(KST);
    }

    /** 지금 한국 시각을 자정부터 센 분으로 */
    public static int nowMinutes() {
        LocalTime t = now().toLocalTime();
        return t.getHour() * 60 + t.getMinute();
    }
}
