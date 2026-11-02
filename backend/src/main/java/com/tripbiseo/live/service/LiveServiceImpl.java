package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.common.external.WeatherClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.AppliedStop;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.RecommendPlace;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.member.service.NotificationService;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 채팅·추천·대안. 지금은 docs/API.md 예시 같은 가짜 값이에요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (679줄 → 여기, StatusService, TrackService로 나눔)
 * 공통 주의
 *  - 오늘 칸은 tripService.getTrip으로 읽고, 고칠 때도 TripService(createStop·updateStop·skipStops)로. 예전처럼 TripMapper를 직접 쓰지 않기
 *  - 장소 판단은 placeService 메서드로 (예전 PlaceService.openInfo·isIndoor static 호출 대신)
 *  - Gemini 프롬프트에 좌표(위도·경도)를 넣지 않기. 날씨는 aiService.askJsonWithWeather(인자 없는 도구, 위치는 서버만 앎)
 */
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private static final int TRIM_STAY_DIVIDE = 2;  // A 시간 단축: 한 칸에서 최대 이만큼 나눈 만큼까지 줄임 (절반)
    private static final int TRIM_BUFFER_MIN = 5;   // A 시간 단축: 늦는 시간보다 이만큼 더 줄여서 여유를 둠
    // B 한 곳 빼기: 뺄 순위 (클수록 먼저 뺌)
    private static final int DROP_RANK_CLOSED = 3;  // 도착하면 문 닫은 곳
    private static final int DROP_RANK_MISSED = 2;  // 계획 시간이 지난 곳
    private static final int DROP_RANK_OUTDOOR = 1; // 실내를 원할 때 야외 장소
    private static final int DROP_RANK_NORMAL = 0;  // 그 밖 (그중 오래 머무는 곳)
    private static final String[] INDOOR_WORDS = {"비", "실내", "추워", "더워", "날씨"}; // 요청에 있으면 실내 위주

    private final TripService tripService;                 // TODO: ④ 오늘 칸 읽기·고치기
    private final PlaceService placeService;               // TODO: ④ 근처 장소·영업 정보·실내 판단
    private final RouteClient routeClient;                 // TODO: ④ 대안 이동 시간 (후보 비교는 isPrecise=false)
    private final WeatherClient weatherClient;             // TODO: ④ 대안에서 비 오는지
    private final AiService aiService;                     // TODO: ④ assist 분류, alternatives
    private final NotificationService notificationService; // TODO: ④ 지연·출발 알림을 ①의 알림함에도 남길 때
    private final LiveStopFinder liveStopFinder;           // 오늘 칸·지금 칸·남은 칸 고르기 (④ 도우미)

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
            AlternativesRequest alternativesRequest = new AlternativesRequest();
            alternativesRequest.setLat(request.getLat());
            alternativesRequest.setLng(request.getLng());
            alternativesRequest.setReason("request");
            alternativesRequest.setRequest(message);
            response.setData(makeAlternatives(memberId, tripId, alternativesRequest));
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
        // 1단계: 남은 일정 꺼내기
        Trip trip = tripService.getTrip(memberId, tripId);
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        List<Stop> remainingStops = getRemainingStops(todayStops, current);
        if (remainingStops.isEmpty()) {
            throw fail(400, "오늘 남은 일정이 없어요.");
        }

        AlternativesResponse response = new AlternativesResponse();
        response.setTitle("남은 일정 대안"); // TODO: ④ 6단계에서 고정 일정이 있으면 "{고정 일정}에 늦지 않는 대안"
        response.setSubtitle("지금 위치·시각에서 다시 계산했어요");
        response.setSource("rule");
        response.getAlternatives().add(makeTrimAlternative(trip, current, remainingStops, request.getLat(), request.getLng()));
        boolean isIndoorWanted = isIndoorRequest(request.getRequest());
        Alternative dropAlternative = makeDropAlternative(trip, current, remainingStops, request.getLat(), request.getLng(), isIndoorWanted);
        if (dropAlternative != null) {
            response.getAlternatives().add(dropAlternative);
        }
        // TODO: ④ 5단계 C 추가, 6단계 key(A·B·C)·recommended 채우기
        return response;
    }

    /** 오늘 칸 중 남은 칸만 순서대로 (지금 있는 칸·다녀온 칸·뺀 칸은 빠짐) */
    private List<Stop> getRemainingStops(List<Stop> todayStops, Stop current) {
        List<Stop> remainingStops = new ArrayList<>();
        for (Stop stop : todayStops) {
            if (liveStopFinder.isRemaining(stop, current)) {
                remainingStops.add(stop);
            }
        }
        return remainingStops;
    }

    // ---------------------------------------------------------------- 대안 A·B·C (3~5단계)

    /**
     * A 시간 단축: 가는 곳은 그대로, 머무는 시간만 줄임.
     * 고정 일정에 늦을 때만, 늦는 시간 + 5분을 고정 일정 앞 칸에서 차례로 줄임 (한 칸에 최대 절반)
     */
    private Alternative makeTrimAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng) {
        // 1. 그대로 갔을 때 계산 (원본을 고치면 B·C 계산까지 바뀌어서 복사본으로)
        List<Stop> trimmedStops = copyStops(remainingStops);
        Alternative alternative = calculateAlternative(trip, current, trimmedStops, lat, lng);

        // 2. 고정 일정에 늦으면 → 늦는 만큼 + 5분 줄이고 다시 계산
        boolean hasFixed = liveStopFinder.findFixedStop(remainingStops, null) != null;
        if (hasFixed && alternative.getSlackMin() < 0) {
            trimStays(trimmedStops, -alternative.getSlackMin() + TRIM_BUFFER_MIN);
            alternative = calculateAlternative(trip, current, trimmedStops, lat, lng);
        }

        // 3. 머묾이 바뀐 칸에 "60분 → 45분" 적기
        int changes = markStayChanges(alternative, remainingStops, trimmedStops);

        // 4. 화면에 보일 이름 채우기
        alternative.setChanges(changes);
        alternative.setShortName("시간 단축");
        alternative.setTitle("머무는 시간 줄이기");
        alternative.setReason("가는 곳은 그대로, 머무는 시간만 줄여요.");
        return alternative;
    }

    /** B 한 곳 빼기: 뺄 곳 하나를 고르고 나머지는 계획대로. 뺄 곳이 없거나 빼면 남는 곳이 없으면 null */
    private Alternative makeDropAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng,
                                            boolean isIndoorWanted) {
        // 1. 뺄 곳 고르기
        Stop dropStop = findDropStop(remainingStops, isIndoorWanted);
        if (dropStop == null || remainingStops.size() == 1) {
            return null;
        }

        // 2. 뺄 곳만 빼고 나머지 목록 만들기 (머묾은 그대로라 복사하지 않음)
        List<Stop> keptStops = new ArrayList<>();
        for (Stop stop : remainingStops) {
            if (stop != dropStop) {
                keptStops.add(stop);
            }
        }

        // 3. 계산기에 넣기
        Alternative alternative = calculateAlternative(trip, current, keptStops, lat, lng);

        // 4. 화면에 보일 이름 채우기
        alternative.setChanges(1);
        alternative.setShortName("한 곳 빼기");
        alternative.setTitle(dropStop.getName() + " 빼기");
        alternative.setReason(getDropReason(getDropRank(dropStop, isIndoorWanted)));
        return alternative;
    }

    /** 뺄 곳 하나: 고정 일정이 아닌 칸 중 뺄 순위가 가장 높은 곳, 같으면 오래 머무는 곳 (없으면 null) */
    private Stop findDropStop(List<Stop> stops, boolean isIndoorWanted) {
        Stop dropStop = null;
        int dropRank = -1;
        for (Stop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed())) {
                continue; // 고정 일정은 빼지 않음
            }
            // TODO: ①②에게 요청함 — trip_stop·Stop에 중요도(importance)가 생기면 "꼭"은 건너뛰고 "빼도 됨"을 먼저
            int rank = getDropRank(stop, isIndoorWanted);
            boolean isHigherRank = rank > dropRank;
            boolean isLongerStay = rank == dropRank && liveStopFinder.getStay(stop) > liveStopFinder.getStay(dropStop);
            if (isHigherRank || isLongerStay) {
                dropStop = stop;
                dropRank = rank;
            }
        }
        return dropStop;
    }

    /** 뺄 순위: 문 닫은 곳 3 > 계획 시간 지난 곳 2 > (실내를 원할 때) 야외 1 > 그 밖 0 */
    private int getDropRank(Stop stop, boolean isIndoorWanted) {
        if (isClosedAtArrival(stop)) {
            return DROP_RANK_CLOSED;
        }
        if (liveStopFinder.isMissed(stop)) {
            return DROP_RANK_MISSED;
        }
        if (isIndoorWanted && !placeService.isIndoor(stop.getCategory())) {
            return DROP_RANK_OUTDOOR;
        }
        return DROP_RANK_NORMAL;
    }

    /** 뺄 순위에 맞는 이유 한 문장 */
    private String getDropReason(int rank) {
        if (rank == DROP_RANK_CLOSED) {
            return "도착하면 문을 닫은 곳을 빼요.";
        }
        if (rank == DROP_RANK_MISSED) {
            return "계획 시간이 지난 곳을 빼요.";
        }
        if (rank == DROP_RANK_OUTDOOR) {
            return "비를 맞을 수 있는 야외 장소를 빼요.";
        }
        return "한 곳을 빼고 나머지는 계획대로 가요.";
    }

    /** 계획 시각(이미 지났으면 지금)에 도착했을 때 문을 닫았나. 장소·영업시간을 모르면 false */
    private boolean isClosedAtArrival(Stop stop) {
        if (stop.getPlaceId() == null) {
            return false; // DB에 없는 장소라 영업시간을 모름
        }
        try {
            Place place = placeService.getPlace(stop.getPlaceId());
            int arrive = Math.max(Geo.toMinutes(stop.getTime()), Geo.nowMinutes());
            OpenInfo open = placeService.getOpenInfo(place, Geo.now().toLocalDate(), arrive, liveStopFinder.getStay(stop));
            return Boolean.FALSE.equals(open.getOpen());
        } catch (ApiException e) {
            return false; // 지워진 장소면 영업 정보 없이 계속
        }
    }

    /** 사용자 요청에 "비·실내·추워·더워·날씨"가 들어 있으면 실내 위주 */
    private boolean isIndoorRequest(String request) {
        if (request == null) {
            return false;
        }
        for (String word : INDOOR_WORDS) {
            if (request.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 고정 일정 앞 칸부터 차례로 줄여서 need분을 채움. 한 칸에서는 최대 절반까지 */
    private void trimStays(List<Stop> stops, int need) {
        for (Stop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed()) || need <= 0) {
                break; // 고정 일정 뒤 칸은 늦는 것과 상관없어서 안 줄임
            }
            int stay = liveStopFinder.getStay(stop);
            int cut = Math.min(stay / TRIM_STAY_DIVIDE, need);
            stop.setStay(stay - cut);
            need = need - cut;
        }
    }

    /** 머묾이 바뀐 칸에 "60분 → 45분"을 적고, 바뀐 칸 수를 돌려줌 */
    private int markStayChanges(Alternative alternative, List<Stop> beforeStops, List<Stop> afterStops) {
        int changes = 0;
        for (int i = 0; i < beforeStops.size(); i++) {
            int before = liveStopFinder.getStay(beforeStops.get(i));
            int after = liveStopFinder.getStay(afterStops.get(i));
            if (before != after) {
                alternative.getStops().get(i).setChange(before + "분 → " + after + "분");
                changes++;
            }
        }
        return changes;
    }

    /** 칸 목록 복사본 */
    private List<Stop> copyStops(List<Stop> stops) {
        List<Stop> copies = new ArrayList<>();
        for (Stop stop : stops) {
            copies.add(copyStop(stop));
        }
        return copies;
    }

    /** 칸 복사본 (대안마다 따로 고쳐도 원본이 안 바뀌게) */
    private Stop copyStop(Stop stop) {
        Stop copy = new Stop();
        copy.setId(stop.getId());
        copy.setTripId(stop.getTripId());
        copy.setDay(stop.getDay());
        copy.setPlaceId(stop.getPlaceId());
        copy.setName(stop.getName());
        copy.setCategory(stop.getCategory());
        copy.setAddress(stop.getAddress());
        copy.setLat(stop.getLat());
        copy.setLng(stop.getLng());
        copy.setTime(stop.getTime());
        copy.setStay(stop.getStay());
        copy.setFixed(stop.getFixed());
        copy.setMode(stop.getMode());
        copy.setState(stop.getState());
        return copy;
    }

    // ---------------------------------------------------------------- 대안 계산 도우미 (2단계)

    /** 바뀐 칸 목록으로 대안 하나 계산: 칸마다 이동 시간·도착 시각, 경로선, 기준 칸 도착과 여유 */
    private Alternative calculateAlternative(Trip trip, Stop current, List<Stop> stops, double lat, double lng) {
        // 2-1 출발점: 지금 어떤 칸에 있으면 그 칸에서 (머무는 시간이 끝나면 출발), 이동 중이면 내 위치에서 지금 출발
        double fromLat = lat;
        double fromLng = lng;
        int clock = Geo.nowMinutes();
        if (current != null) {
            fromLat = current.getLat();
            fromLng = current.getLng();
            clock = Math.max(clock, liveStopFinder.getLeaveMinutes(current));
        }

        // 2-2 칸마다 이동 시간·도착 시각 (대안 후보를 여러 개 비교하니 길찾기 API 없이 거리로 추정: isPrecise=false)
        Alternative alternative = new Alternative();
        List<Integer> arriveList = new ArrayList<>();
        for (Stop stop : stops) {
            Route route = routeClient.getRoute(fromLat, fromLng, stop.getLat(), stop.getLng(), stop.getMode(), trip.getTransports(), false);
            int arrive = clock + route.getMinutes();
            arriveList.add(arrive);
            alternative.getStops().add(toAltStop(stop, arrive, route.getMinutes()));
            if (route.getCoords() != null) {
                alternative.getCoords().addAll(route.getCoords());
            }
            clock = liveStopFinder.getStartMinutes(stop, arrive) + liveStopFinder.getStay(stop);
            fromLat = stop.getLat();
            fromLng = stop.getLng();
        }

        // 2-3 기준 칸(첫 고정 일정, 없으면 마지막 칸)에 몇 시 도착, 여유 몇 분
        setFinalArrive(alternative, stops, arriveList);
        return alternative;
    }

    /** 기준 칸 = 첫 고정 일정, 없으면 마지막 칸. 여유(분) = 계획 시각 − 예상 도착, 음수면 지각 */
    private void setFinalArrive(Alternative alternative, List<Stop> stops, List<Integer> arriveList) {
        int basisIndex = stops.size() - 1;
        for (int i = 0; i < stops.size(); i++) {
            if (Boolean.TRUE.equals(stops.get(i).getFixed())) {
                basisIndex = i;
                break;
            }
        }
        int arrive = arriveList.get(basisIndex);
        alternative.setFinalArrive(Geo.toHhmm(arrive));
        alternative.setSlackMin(Geo.toMinutes(stops.get(basisIndex).getTime()) - arrive);
    }

    /** Stop → 화면용 AltStop. eta는 예상 도착, 고정 일정은 계획 시각 그대로 (API.md). change는 대안마다 따로 채움 */
    private AltStop toAltStop(Stop stop, int arrive, int moveMin) {
        AltStop altStop = new AltStop();
        altStop.setId(stop.getId());
        altStop.setPlaceId(stop.getPlaceId());
        altStop.setName(stop.getName());
        altStop.setCategory(stop.getCategory());
        altStop.setLat(stop.getLat());
        altStop.setLng(stop.getLng());
        altStop.setStay(stop.getStay());
        altStop.setFixed(Boolean.TRUE.equals(stop.getFixed()));
        altStop.setEta(Boolean.TRUE.equals(stop.getFixed()) ? stop.getTime() : Geo.toHhmm(arrive));
        altStop.setMoveMin(moveMin);
        altStop.setClosingSoon(false);
        return altStop;
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
