package com.tripbiseo.live.service;

import java.util.List;

import com.tripbiseo.live.dto.TrackPoint;
import com.tripbiseo.live.dto.TrackRequest;

/** ④ GPS 기록 + 도착·출발 판정 (예전 LiveService에서 나눔) */
public interface TrackService {

    /** GPS 한 점 저장 + 오늘 todo 칸 150m 안이면 도착, 도착한 칸에서 200m 밖 두 번 연속이면 출발 */
    void createTrack(long memberId, long tripId, TrackRequest request);

    /** 여행의 GPS 점 (기록 순). ⑤ 회고가 부름 — 내 여행인지는 부르는 쪽이 먼저 확인 */
    List<TrackPoint> getTrackList(long tripId);

    /** 회원의 위치 기록 모두 지우기 (① 위치 기록 화면이 부름) */
    void deleteTrackList(long memberId);
}
