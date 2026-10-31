package com.tripbiseo.live.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.external.WeatherClient;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.AppliedStop;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.live.dto.AreaInfo;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.FixedStatus;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.LeaveInfo;
import com.tripbiseo.live.dto.LiveAlert;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.NextRoute;
import com.tripbiseo.live.dto.RecommendPlace;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.live.dto.StatusStop;
import com.tripbiseo.member.service.NotificationService;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 판별. 지금은 docs/API.md 예시 같은 가짜 값이에요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (679줄 → 여기와 TrackService로 나눔)
 * 공통 주의
 *  - 오늘 칸은 tripService.getTrip으로 읽고, 고칠 때도 TripService(createStop·updateStop·skipStops)로. 예전처럼 TripMapper를 직접 쓰지 않기
 *  - 장소 판단은 placeService 메서드로 (예전 PlaceService.openInfo·isIndoor static 호출 대신)
 *  - Gemini 프롬프트에 좌표(위도·경도)를 넣지 않기. 날씨는 aiService.askJsonWithWeather(인자 없는 도구, 위치는 서버만 앎)
 */
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private final TripService tripService;                 // TODO: ④ 오늘 칸 읽기·고치기
    private final PlaceService placeService;               // TODO: ④ 영업 정보·근처 장소·실내 판단
    private final RouteClient routeClient;                 // TODO: ④ 이동 시간 (후보 비교는 isPrecise=false)
    private final WeatherClient weatherClient;             // TODO: ④ status의 weather
    private final AiService aiService;                     // TODO: ④ assist 분류, alternatives
    private final NotificationService notificationService; // TODO: ④ 지연·출발 알림을 ①의 알림함에도 남길 때

    /** 지금 상황 다시 계산 */
    // TODO: ④ 지금 상황. 공통 오류 → 오늘(day)·남은 일정 → 칸마다 eta·reach·영업 정보 → alert 하나(우선순위) → leave·fixed·hint·replan·area·next.
    //       참고: 예전 LiveService.status/today/start/alert/rain.
    //       주의: 날씨는 weatherClient.getForecast(서버가 위치로 직접), 실패하면 weather = null. 구글 검색 대체는 쓰지 않기
    @Override
    public StatusResponse getStatus(long memberId, long tripId, LocationRequest request) {
        StatusResponse response = new StatusResponse();
        response.setNow("14:20");
        response.setCurrentStopId(102L);
        response.setAlert(sampleAlert());
        response.setLeave(sampleLeave());
        response.setFixed(sampleFixed());
        response.setReplan(false);
        response.setArea(sampleArea(request));
        response.getStops().add(statusStop(101L, "e70caff5-27cf-5c76-a1f3-201b7032d672", "경복궁", "관광지", 37.5796, 126.977, "10:00", 90, false, "done"));
        response.getStops().add(statusStop(102L, null, "카페OO", "카페", 37.5745, 126.9849, "13:30", 55, false, "here"));
        response.getStops().add(statusStop(103L, null, "세종문화회관", "문화시설", 37.5725, 126.9759, "19:00", 120, true, "fixed"));
        response.setNext(sampleNext());
        return response;
    }

    /** 여행 중 채팅: 말 분류 */
    // TODO: ④ 채팅. message 1~300자 → aiService.askJson(분류 프롬프트, 분류 DTO) → recommend면 ask-radius, change면 makeAlternatives(reason "request"), 그 밖은 text.
    //       참고: 예전 LiveService.assist. 주의: Gemini 실패(ApiException)는 catch해서 낱말 규칙으로 (오류 내지 않기). 프롬프트에 좌표 넣지 않기.
    //       확인 필요(④): 비 요청일 때도 대안 A·B·C를 계산할지 (API.md)
    @Override
    public AssistResponse assist(long memberId, long tripId, AssistRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage();
        AssistResponse response = new AssistResponse();
        if (message.contains("추천") || message.contains("카페") || message.contains("맛집")) {
            response.setKind("ask-radius");
            response.setCategory("카페");
        } else if (message.contains("바꿔") || message.contains("늦")) {
            response.setKind("alternatives");
            response.setData(sampleAlternatives());
        } else {
            response.setKind("text");
            response.setText("다음 일정은 19:00 세종문화회관이에요. 지금 20분 여유가 있어요.");
        }
        return response;
    }

    /** 반경 안 평점 상위 3곳 */
    // TODO: ④ 추천. radius 없으면 400 "radius 값이 필요해요.", 100~3000 밖이면 400 → placeService.getNearbyPlaceList(PostGIS) →
    //       평점 없는 곳·도착 때 닫힌 곳 빼고(closedSkipped) 보정 평점 순 3개 + 영업 정보. 참고: 예전 LiveService.recommend, PlaceService.recommend
    @Override
    public RecommendResponse recommend(long memberId, long tripId, RecommendRequest request) {
        RecommendPlace place = new RecommendPlace();
        place.setId("naver-2093701183");
        place.setName("카페OO");
        place.setCategory("카페,디저트");
        place.setAddress("서울 종로구 인사동길 12");
        place.setLat(37.5745);
        place.setLng(126.9849);
        place.setRating(4.7);
        place.setReviewCount(320);
        place.setNaverUrl("https://map.naver.com/p/entry/place/2093701183");
        place.setDistance(180);
        place.setOpen(true);
        place.setCloseAt("22:00");
        place.setMinutesLeft(455);
        place.setClosingSoon(false);
        RecommendResponse response = new RecommendResponse();
        response.getPlaces().add(place);
        response.setClosedSkipped(1);
        return response;
    }

    /** 추천 장소를 다음 일정으로 */
    // TODO: ④ 일정에 넣기. placeService.getPlace(없으면 404) → 새 칸(카페 40분, 아니면 60분) tripService.createStop →
    //       남은 칸 시각 다시 계산해 tripService.updateStop, 고정 일정에 늦으면 앞 칸 머무는 시간을 최대 절반까지. 참고: 예전 LiveService.insert.
    //       주의: 다른 번호(②)의 쓰기 메서드를 여러 번 부르니 @Transactional 유지
    @Override
    @Transactional
    public InsertResponse addRecommendedPlace(long memberId, long tripId, InsertRequest request) {
        InsertResponse response = new InsertResponse();
        response.setTrip(tripService.getTrip(memberId, tripId));
        response.setMessage("카페OO을(를) 다음 일정으로 넣었어요. 세종문화회관에는 12분 여유가 있어요.");
        return response;
    }

    /** 대안 A·B·C */
    // TODO: ④ 대안. 남은 일정 없으면 400 "오늘 남은 일정이 없어요." → 규칙 대안(A 시간 단축 / B 한 곳 빼기 / C 장소 변경) →
    //       aiService.isReady()면 askJsonWithWeather로 Gemini 대안, 실패하면 규칙만. 3개 채우고 key A·B·C, recommended 하나.
    //       참고: 예전 LiveService.alternatives/fit/chain/shape/substitute/recommendBest.
    //       주의: 예전 프롬프트의 "위치 (위도, 경도)"는 빼기. 날씨 도구는 인자 없음. 예전 askWithSearch(구글 검색 대체)는 쓰지 않기.
    //             근처 후보는 placeService.getNearbyPlaceList(PostGIS)로
    @Override
    public AlternativesResponse makeAlternatives(long memberId, long tripId, AlternativesRequest request) {
        return sampleAlternatives();
    }

    /** 고른 대안을 일정에 반영 */
    // TODO: ④ 대안 적용. stops 없으면 400 "적용할 대안이 없어요." → id가 남은 칸이면 eta·stay로 updateStop(고정 일정 시각은 그대로),
    //       id가 없으면 createStop, 대안에 없는 남은 칸은 skipStops → 오늘 칸 tag(done/kept/changed/added). 참고: 예전 LiveService.apply/nextAlert.
    //       주의: ②의 쓰기 메서드를 여러 번 부르니 @Transactional 유지
    @Override
    @Transactional
    public ApplyResponse applyAlternative(long memberId, long tripId, ApplyRequest request) {
        ApplyResponse response = new ApplyResponse();
        response.setKey(request.getKey() == null ? "A" : request.getKey());
        response.setMessage("세종문화회관에 늦지 않게 일정을 바꿨어요!");
        response.setNextAlert("청계천에서 출발할 시각(15:07) 10분 전부터 1분마다 알려드릴게요. 늦게 도착하면 도착한 시각 기준으로 다시 맞춰요.");
        response.getStops().add(appliedStop(102L, "13:30", "카페OO", "카페", 55, "done", null));
        response.getStops().add(appliedStop(103L, "14:37", "청계천", "관광지", 30, "changed", "60분 → 30분"));
        response.getStops().add(appliedStop(105L, "19:00", "세종문화회관", "문화시설", 120, "kept", null));
        return response;
    }

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static LiveAlert sampleAlert() {
        LiveAlert alert = new LiveAlert();
        alert.setKey("leave-102-5");
        alert.setLevel("warn");
        alert.setTitle("5분 뒤 출발해야 해요");
        alert.setText("카페OO 출발 14:25 → 청계천");
        alert.setRepeat(true);
        return alert;
    }

    private static LeaveInfo sampleLeave() {
        LeaveInfo leave = new LeaveInfo();
        leave.setStopId(102L);
        leave.setName("카페OO");
        leave.setLeaveAt("14:25");
        leave.setMinutesLeft(5);
        leave.setTo("청계천");
        leave.setAtStop(true);
        return leave;
    }

    private static FixedStatus sampleFixed() {
        FixedStatus fixed = new FixedStatus();
        fixed.setId(103L);
        fixed.setName("세종문화회관");
        fixed.setTime("19:00");
        fixed.setEta("18:40");
        fixed.setSlackMin(20);
        fixed.setLat(37.5725);
        fixed.setLng(126.9759);
        return fixed;
    }

    private static AreaInfo sampleArea(LocationRequest request) {
        AreaInfo area = new AreaInfo();
        area.setSlackMin(20);
        area.setBasis("세종문화회관");
        area.setBasisTime("19:00");
        area.setLat(request.getLat());
        area.setLng(request.getLng());
        return area;
    }

    private static NextRoute sampleNext() {
        NextRoute next = new NextRoute();
        next.setId(103L);
        next.setMinutes(12);
        next.getCoords().add(new double[] {37.5745, 126.9849});
        next.getCoords().add(new double[] {37.5725, 126.9759});
        next.setSource("estimate");
        return next;
    }

    private static StatusStop statusStop(long id, String placeId, String name, String category, double lat, double lng,
                                         String time, int stay, boolean isFixed, String reach) {
        StatusStop stop = new StatusStop();
        stop.setId(id);
        stop.setPlaceId(placeId);
        stop.setName(name);
        stop.setCategory(category);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setEta(time);
        stop.setReach(reach);
        stop.setClosingSoon(false);
        return stop;
    }

    private static AlternativesResponse sampleAlternatives() {
        Alternative alternative = new Alternative();
        alternative.setKey("A");
        alternative.setShortName("시간 단축");
        alternative.setTitle("머무는 시간 줄이기");
        alternative.setReason("가는 곳은 그대로, 머무는 시간만 줄여요.");
        alternative.setRecommended(true);
        alternative.setFinalArrive("18:55");
        alternative.setSlackMin(5);
        alternative.setChanges(1);
        alternative.getCoords().add(new double[] {37.5745, 126.9849});
        alternative.getCoords().add(new double[] {37.5725, 126.9759});
        alternative.getStops().add(altStop(103L, "청계천", "관광지", 37.569, 126.978, 30, false, "14:37", 12, "60분 → 30분"));
        alternative.getStops().add(altStop(105L, "세종문화회관", "문화시설", 37.5725, 126.9759, 120, true, "19:00", 15, null));
        AlternativesResponse response = new AlternativesResponse();
        response.setTitle("세종문화회관에 늦지 않는 대안");
        response.setSubtitle("지금 위치·시각에서 다시 계산했어요");
        response.setSource("rule");
        response.getAlternatives().add(alternative);
        return response;
    }

    private static AltStop altStop(long id, String name, String category, double lat, double lng, int stay, boolean isFixed,
                                   String eta, int moveMin, String change) {
        AltStop stop = new AltStop();
        stop.setId(id);
        stop.setName(name);
        stop.setCategory(category);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setEta(eta);
        stop.setMoveMin(moveMin);
        stop.setChange(change);
        stop.setClosingSoon(false);
        return stop;
    }

    private static AppliedStop appliedStop(long id, String time, String name, String category, int stay, String tag, String note) {
        AppliedStop stop = new AppliedStop();
        stop.setId(id);
        stop.setTime(time);
        stop.setName(name);
        stop.setCategory(category);
        stop.setStay(stay);
        stop.setTag(tag);
        stop.setNote(note);
        return stop;
    }
}
