package com.tripbiseo.plan.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.tripbiseo.common.auth.LoginUser;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.plan.dto.AreaStat;
import com.tripbiseo.plan.dto.DongShape;
import com.tripbiseo.plan.dto.DongType;
import com.tripbiseo.plan.dto.LinkRequest;
import com.tripbiseo.plan.dto.LinkResponse;
import com.tripbiseo.plan.dto.RecommendedLink;
import com.tripbiseo.plan.dto.RouteOptionsResponse;
import com.tripbiseo.plan.dto.RouteOrderRequest;
import com.tripbiseo.plan.dto.RouteOrderResponse;
import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.CaptureResponse;
import com.tripbiseo.plan.dto.StayCaptureResponse;
import com.tripbiseo.plan.service.AreaStatService;
import com.tripbiseo.plan.service.CaptureService;
import com.tripbiseo.plan.service.LinkService;
import com.tripbiseo.plan.service.PlanService;

import lombok.RequiredArgsConstructor;

/** ③ AI 일정 만들기 (저장 전 초안): 대화 → 조건 → 초안. 점검(/api/plans/check)은 ②의 PlanCheckController */
@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private static final Logger log = LoggerFactory.getLogger(PlanController.class);
    private static final long STREAM_TIMEOUT_MS = 120_000L; // 진행 알림 연결은 2분까지 (Gemini가 느릴 때도 안 끊기게)
    private static final int STREAM_THREADS = 4;            // 진행 알림으로 동시에 만들 수 있는 일정 수

    // 일정 만들기를 요청 스레드 밖에서 돌릴 작업자 4개 (요청 스레드는 바로 돌려주고, 진행 알림은 이 작업자가 보냄)
    private final ExecutorService streamThreads = Executors.newFixedThreadPool(STREAM_THREADS);
    private final PlanService planService;
    private final CaptureService captureService;
    private final LinkService linkService;
    private final AreaStatService areaStatService;
    private final com.tripbiseo.plan.service.PlanCandidateFinder planCandidateFinder;
    private final com.tripbiseo.trip.service.PlaceService placeService;
    private final LoginUser loginUser;

    /** 사용자 말 → 여행 조건 정리 */
    @PostMapping("/chat")
    public PlanChatResponse chatPlan(@RequestBody PlanChatRequest request) {
        return planService.chatPlan(request);
    }

    /** 한 구의 행정동 특색 (동 지도 색칠) */
    @PostMapping("/dong-types")
    public List<DongType> getDongTypeList(@RequestBody List<DongShape> dongs) {
        return areaStatService.getDongTypeList(dongs);
    }

    /** 이동 시간이 짧은 순서 후보 ([동선 최적화]) */
    @PostMapping("/route-options")
    public RouteOptionsResponse getRouteOptions(@RequestBody RouteOrderRequest request) {
        return planService.getRouteOptions(request);
    }

    /** 하루 일정의 새 순서 ([동선 정리]) */
    @PostMapping("/route-order")
    public RouteOrderResponse getRouteOrder(@RequestBody RouteOrderRequest request) {
        return planService.getRouteOrder(request);
    }

    /** 조건 → 일정 초안 (저장 안 함) */
    @PostMapping("/generate")
    public PlanGenerateResponse generatePlan(@RequestBody PlanGenerateRequest request) {
        return planService.generatePlan(loginUser.id(), request);
    }

    /**
     * 조건 → 일정 초안 + 진행 알림 (11/04). SSE: 응답 하나를 열어 두고 서버가 여러 번 나눠 보내는 방식.
     * 보내는 것: progress(단계마다 PlanProgress) 여러 번 → 마지막에 done(초안, /generate 응답과 같은 모양) 또는 fail({status, message})
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generatePlanStream(@RequestBody PlanGenerateRequest request) {
        long memberId = loginUser.id(); // 세션은 요청 스레드에서만 읽을 수 있어서 먼저 꺼내 둠
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS); // SseEmitter: 응답을 열어 두고 여러 번 보내는 Spring 도구
        streamThreads.execute(() -> runGenerate(memberId, request, emitter));
        return emitter;
    }

    /** 작업자 스레드에서: 일정 만들기 → 단계마다 progress, 끝나면 done 또는 fail 보내고 연결 닫기 */
    private void runGenerate(long memberId, PlanGenerateRequest request, SseEmitter emitter) {
        try {
            PlanGenerateResponse draft = planService.generatePlan(memberId, request, step -> send(emitter, "progress", step));
            send(emitter, "done", draft);
        } catch (ApiException e) {
            send(emitter, "fail", Map.of("status", e.getStatus(), "message", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("일정 만들기 중 처리하지 못한 오류", e);
            send(emitter, "fail", Map.of("status", 500, "message", "서버 오류가 났어요. 잠시 후 다시 시도해 주세요."));
        }
        emitter.complete();
    }

    /** 이름 붙은 알림 하나 보내기. 화면이 닫혀 연결이 끊겼으면 보내지 않고 넘어감 */
    private void send(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            log.debug("진행 알림 연결이 이미 끝남: {}", e.getMessage());
        }
    }

    /** 예약 캡처 사진 → 장소 이름·날짜·시각 (고정 일정 후보, 저장 안 함). MultipartFile: 화면이 올린 파일 */
    @PostMapping("/capture")
    public CaptureResponse readCapture(@RequestParam(value = "image", required = false) MultipartFile image) throws IOException {
        byte[] bytes = image == null ? null : image.getBytes();
        return captureService.readCapture(bytes, image == null ? null : image.getContentType());
    }

    /** 숙소 예약 캡처 사진 → 숙소 이름·주소·체크인·체크아웃 + 위치 (숙소 후보, 저장 안 함) */
    @PostMapping("/capture/stay")
    public StayCaptureResponse readStayCapture(@RequestParam(value = "image", required = false) MultipartFile image) throws IOException {
        byte[] bytes = image == null ? null : image.getBytes();
        return captureService.readStayCapture(bytes, image == null ? null : image.getContentType());
    }

    /** 가고 싶은 곳 추천 게시물 */
    @GetMapping("/links/recommended")
    public List<RecommendedLink> getRecommendedLinkList() {
        return linkService.getRecommendedLinkList();
    }

    /** 어디로 가요? 추천 지역: 종류마다 리뷰가 많은 서울 구 */
    @GetMapping("/areas")
    public List<AreaStat> getAreaStatList() {
        return areaStatService.getAreaStatList();
    }

    /** 인스타 게시물·릴스 링크 → 글에 나온 장소 (저장 안 함) */
    @GetMapping("/places")
    public java.util.List<com.tripbiseo.trip.dto.PlaceCard> getManualPlaceList(@org.springframework.web.bind.annotation.RequestParam double lat,
                                                                                @org.springframework.web.bind.annotation.RequestParam double lng,
                                                                                @org.springframework.web.bind.annotation.RequestParam String kind) {
        java.util.List<com.tripbiseo.trip.dto.PlaceCard> cards = new java.util.ArrayList<>();
        for (com.tripbiseo.trip.dto.Place place : planCandidateFinder.findKindPlaces(lat, lng, kind)) {
            cards.add(placeService.getPlaceCard(place, lat, lng));
        }
        return cards;
    }

    @PostMapping("/links")
    public LinkResponse findLinkPlaces(@RequestBody LinkRequest request) {
        return linkService.findPlaces(request);
    }
}
