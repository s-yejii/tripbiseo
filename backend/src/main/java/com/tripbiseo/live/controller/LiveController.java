package com.tripbiseo.live.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.dto.OkResponse;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.live.dto.TrackRequest;
import com.tripbiseo.live.service.LiveService;
import com.tripbiseo.live.service.TrackService;

import lombok.RequiredArgsConstructor;

/** ④ 여행 중 화면. 모든 요청 본문에 지금 위치(lat, lng)가 들어 있어요 */
@RestController
@RequestMapping("/api/trips/{id}/live")
@RequiredArgsConstructor
public class LiveController {

    private final LiveService liveService;
    private final TrackService trackService;
    private final LoginUser loginUser;

    /** GPS 한 점 저장 + 도착·출발 기록 */
    @PostMapping("/track")
    public OkResponse createTrack(@PathVariable long id, @RequestBody TrackRequest request) {
        trackService.createTrack(loginUser.id(), id, request);
        return new OkResponse();
    }

    /** 지금 상황 다시 계산 */
    @PostMapping("/status")
    public StatusResponse getStatus(@PathVariable long id, @RequestBody LocationRequest request) {
        return liveService.getStatus(loginUser.id(), id, request);
    }

    /** 여행 중 채팅 */
    @PostMapping("/assist")
    public AssistResponse assist(@PathVariable long id, @RequestBody AssistRequest request) {
        return liveService.assist(loginUser.id(), id, request);
    }

    /** 반경 안 평점 상위 3곳 추천 */
    @PostMapping("/recommend")
    public RecommendResponse recommend(@PathVariable long id, @RequestBody RecommendRequest request) {
        return liveService.recommend(loginUser.id(), id, request);
    }

    /** 추천 장소를 다음 일정으로 넣기 */
    @PostMapping("/insert")
    public InsertResponse addRecommendedPlace(@PathVariable long id, @RequestBody InsertRequest request) {
        return liveService.addRecommendedPlace(loginUser.id(), id, request);
    }

    /** 대안 A·B·C 만들기 */
    @PostMapping("/alternatives")
    public AlternativesResponse makeAlternatives(@PathVariable long id, @RequestBody AlternativesRequest request) {
        return liveService.makeAlternatives(loginUser.id(), id, request);
    }

    /** 고른 대안을 일정에 반영 */
    @PostMapping("/apply")
    public ApplyResponse applyAlternative(@PathVariable long id, @RequestBody ApplyRequest request) {
        return liveService.applyAlternative(loginUser.id(), id, request);
    }
}
