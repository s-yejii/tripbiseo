package com.tripbiseo.common;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** 거리·시간 계산 도우미 */
public final class Geo {
    private Geo() {}

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 두 좌표 사이 거리(m) — 하버사인 */
    public static double meters(double lat1, double lng1, double lat2, double lng2) {
        double r = Math.PI / 180, dLat = (lat2 - lat1) * r, dLng = (lng2 - lng1) * r;
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1 * r) * Math.cos(lat2 * r) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * 6371e3 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** 반경(m) → 위도·경도 상자 [minLat, maxLat, minLng, maxLng] */
    public static double[] box(double lat, double lng, double radius) {
        double dLat = radius / 111320, dLng = radius / (111320 * Math.cos(Math.toRadians(lat)));
        return new double[] {lat - dLat, lat + dLat, lng - dLng, lng + dLng};
    }

    /** "14:30" → 870 */
    public static int min(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) return 0;
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** 870 → "14:30" (자정을 넘으면 24시간 안으로) */
    public static String hhmm(int min) {
        int m = ((min % 1440) + 1440) % 1440;
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    public static ZonedDateTime now() {
        return ZonedDateTime.now(KST);
    }

    public static int nowMin() {
        LocalTime t = now().toLocalTime();
        return t.getHour() * 60 + t.getMinute();
    }
}
