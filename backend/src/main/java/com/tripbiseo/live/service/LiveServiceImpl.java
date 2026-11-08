package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.ChatIntent;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.LocationRequest;
import com.tripbiseo.live.dto.PlaceAskResult;
import com.tripbiseo.live.dto.PlaceTagCount;
import com.tripbiseo.live.dto.RecommendPlace;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 AI 채팅 → 근처 추천 → 일정에 넣기. 대안은 AlternativeServiceImpl, 지금 상황은 StatusServiceImpl.
 * AI 원칙: 규칙 먼저, 애매할 때만 Gemini(분류·답 문장만). 추천·시각 계산은 코드로. 좌표는 프롬프트에 넣지 않음.
 */
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MIN_RADIUS_M = 100;
    private static final int MAX_RADIUS_M = 3000;
    private static final int RECOMMEND_COUNT = 3;
    private static final int CAFE_STAY_MIN = 40;          // 일정에 넣기: 카페·디저트면 40분, 아니면 60분 (API.md)
    private static final int OTHER_STAY_MIN = 60;
    private static final double WALK_METERS_PER_MIN = 75; // 도착 예상: 걸어서 분당 75m, 길 돌아감 1.3배
    private static final double ROAD_FACTOR = 1.3;
    private static final String FOOD_WORDS = ".*(식당|맛집|음식|밥|한식|중식|일식|양식|고기|분식).*";
    private static final String NOT_FOOD_WORDS = ".*(카페|디저트|베이커리|숙박|관광|문화|공원|쇼핑).*";
    private static final String DRINK_WORDS = ".*(주점|이자카야|호프|맥주|와인|포차|칵테일).*";

    private final TripService tripService;
    private final PlaceService placeService;
    private final StatusService statusService;             // 질문 답할 때 오늘 상황 (RAG의 근거)
    private final AlternativeService alternativeService;   // "일정 바꿔줘" → 대안, 일정에 넣기 → 대안 적용 재사용
    private final LiveTripReader liveTripReader;           // 공통 오류 검사 + 진행 중인 여행
    private final LiveStopFinder liveStopFinder;
    private final AlternativeCalculator alternativeCalculator;
    private final LiveChatClassifier liveChatClassifier;   // 말 분류 (규칙 → Gemini)
    private final LiveChatAnswerer liveChatAnswerer;       // 질문 답 (템플릿 → Gemini RAG)
    private final PlaceTagReader placeTagReader;           // 리뷰 태그 점수·이유 칩
    private final PlaceAskService placeAskService;         // 추천 질문 → SQL로 바꿔 찾기 (Text-to-SQL, 11/08)

    /** 여행 중 채팅: 말 분류 → 추천이면 질문으로 바로 찾기(못 찾으면 반경 묻기), 바꾸기면 대안, 질문이면 오늘 상황으로 답 */
    @Override
    public AssistResponse assist(long memberId, long tripId, AssistRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw fail(400, "메시지를 1~300자로 입력해 주세요.");
        }
        liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        ChatIntent intent = liveChatClassifier.classify(message);
        AssistResponse response = new AssistResponse();
        if ("recommend".equals(intent.getKind())) {
            answerRecommend(response, memberId, tripId, request, message, intent);
        } else if ("change".equals(intent.getKind())) {
            response.setKind("alternatives");
            response.setData(alternativeService.makeAlternatives(memberId, tripId, toAlternativesRequest(request, message)));
        } else {
            response.setKind("text");
            response.setText(liveChatAnswerer.answer(message, statusService.getStatus(memberId, tripId, toLocation(request))));
        }
        return response;
    }

    /**
     * 반경 안 추천 3곳: PostGIS 반경 검색 → 평점 없는 곳 빼기 → 점수(보정 평점 + 원하는 리뷰 태그) 순
     * → 도착할 때 닫힌 곳 빼기(closedSkipped) → 영업 정보 + 이유 칩
     */
    @Override
    public RecommendResponse recommend(long memberId, long tripId, RecommendRequest request) {
        if (request.getRadius() == null) {
            throw fail(400, "radius 값이 필요해요.");
        }
        liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        if (request.getRadius() < MIN_RADIUS_M || request.getRadius() > MAX_RADIUS_M) {
            throw fail(400, "반경은 100m~3km예요.");
        }
        List<Place> candidates = findCandidates(request);
        Map<String, PlaceTagCount> tagMap = placeTagReader.countTags(candidates);
        List<String> wantedTags = request.getTags() == null ? new ArrayList<>() : request.getTags();
        sortByScore(candidates, tagMap, wantedTags);
        return pickOpenPlaces(candidates, tagMap, wantedTags, getStay(request.getCategory()));
    }

    /**
     * 추천 장소를 바로 다음 일정으로. 새 칸을 맨 앞에 둔 "대안"을 계산해서 대안 적용(applyAlternative)으로 저장해요.
     * 고정 일정에 늦으면 고정 일정 바로 앞 칸 머무는 시간을 최대 절반까지 줄임 (API.md)
     */
    @Override
    @Transactional
    public InsertResponse addRecommendedPlace(long memberId, long tripId, InsertRequest request) {
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        Place place = placeService.getPlace(request.getPlaceId()); // 없으면 404 "장소를 찾을 수 없어요."
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());

        List<Stop> plan = new ArrayList<>();
        plan.add(toNewStop(place, liveStopFinder.getToday(trip)));
        for (Stop stop : todayStops) {
            if (liveStopFinder.isRemaining(stop, current)) {
                plan.add(stop);
            }
        }
        plan = alternativeCalculator.copyStops(plan); // 원본을 고치지 않게 복사본으로 계산
        Alternative result = alternativeCalculator.calculate(trip, current, plan, request.getLat(), request.getLng());
        String shortened = shortenBeforeFixed(plan, result);
        if (shortened != null) {
            result = alternativeCalculator.calculate(trip, current, plan, request.getLat(), request.getLng());
        }
        alternativeService.applyAlternative(memberId, tripId, toApplyRequest(result, request));

        InsertResponse response = new InsertResponse();
        response.setTrip(tripService.getTrip(memberId, tripId));
        response.setMessage(getInsertMessage(place, plan, result, shortened));
        return response;
    }

    // ---------------------------------------------------------------- 채팅 도우미

    /** 추천 질문: Text-to-SQL로 바로 찾기 (places). AI로 못 찾으면 예전처럼 반경 묻기 (ask-radius) */
    private void answerRecommend(AssistResponse response, long memberId, long tripId, AssistRequest request,
                                 String message, ChatIntent intent) {
        PlaceAskResult found = placeAskService.findPlaces(memberId, tripId, message, request.getLat(), request.getLng());
        if (found.isFallback()) {
            response.setKind("ask-radius");
            response.setCategory(intent.getCategory());
            response.setTags(intent.getTags());
            return;
        }
        response.setKind("places");
        response.setFound(found);
    }

    private AlternativesRequest toAlternativesRequest(AssistRequest request, String message) {
        AlternativesRequest alternativesRequest = new AlternativesRequest();
        alternativesRequest.setLat(request.getLat());
        alternativesRequest.setLng(request.getLng());
        alternativesRequest.setReason("request");
        alternativesRequest.setRequest(message);
        return alternativesRequest;
    }

    private LocationRequest toLocation(AssistRequest request) {
        LocationRequest location = new LocationRequest();
        location.setLat(request.getLat());
        location.setLng(request.getLng());
        return location;
    }

    // ---------------------------------------------------------------- 추천 도우미

    /** 반경 안 후보 (평점 있는 곳만). 식당·술집은 분류 말이 여러 가지라 SQL은 전체로 찾고 여기서 거름 */
    private List<Place> findCandidates(RecommendRequest request) {
        String category = request.getCategory() == null ? "" : request.getCategory().trim();
        boolean isFood = category.matches(FOOD_WORDS);
        boolean isDrink = category.contains("술");
        String sqlCategory = isFood || isDrink || category.isEmpty() ? null : category;
        List<Place> candidates = new ArrayList<>();
        for (Place place : placeService.getNearbyPlaceList(request.getLat(), request.getLng(), request.getRadius(), sqlCategory)) {
            if (place.getRating() == null) {
                continue;
            }
            String placeCategory = place.getCategory() == null ? "" : place.getCategory();
            if (isFood && placeCategory.matches(NOT_FOOD_WORDS)) {
                continue;
            }
            if (isDrink && !placeCategory.matches(DRINK_WORDS)) {
                continue;
            }
            candidates.add(place);
        }
        return candidates;
    }

    /** 점수 높은 순 정렬 (sort에 넘기는 규칙: 두 장소의 점수를 거꾸로 비교해서 큰 게 앞) */
    private void sortByScore(List<Place> places, Map<String, PlaceTagCount> tagMap, List<String> wantedTags) {
        Map<String, Double> scoreMap = new HashMap<>();
        for (Place place : places) {
            scoreMap.put(place.getId(), placeTagReader.getScore(place, tagMap.get(place.getId()), wantedTags));
        }
        places.sort((first, second) -> Double.compare(scoreMap.get(second.getId()), scoreMap.get(first.getId())));
    }

    /** 점수 순으로 보면서 도착할 때 열려 있는 곳 3개. 닫힌 곳은 세서 closedSkipped */
    private RecommendResponse pickOpenPlaces(List<Place> places, Map<String, PlaceTagCount> tagMap, List<String> wantedTags, int stay) {
        RecommendResponse response = new RecommendResponse();
        LocalDate today = Geo.now().toLocalDate();
        int closedSkipped = 0;
        for (Place place : places) {
            int walkMinutes = (int) Math.ceil(place.getDistance() * ROAD_FACTOR / WALK_METERS_PER_MIN);
            OpenInfo open = placeService.getOpenInfo(place, today, Geo.nowMinutes() + walkMinutes, stay);
            if (Boolean.FALSE.equals(open.getOpen())) {
                closedSkipped++;
                continue;
            }
            response.getPlaces().add(toRecommendPlace(place, open, placeTagReader.getReasons(tagMap.get(place.getId()), wantedTags)));
            if (response.getPlaces().size() == RECOMMEND_COUNT) {
                break;
            }
        }
        response.setClosedSkipped(closedSkipped);
        return response;
    }

    /** 장소 카드 + 영업 정보 + 이유 칩 */
    private RecommendPlace toRecommendPlace(Place place, OpenInfo open, List<String> reasons) {
        RecommendPlace recommend = new RecommendPlace();
        recommend.setId(place.getId());
        recommend.setName(place.getName());
        recommend.setCategory(place.getCategory());
        recommend.setAddress(place.getAddress());
        recommend.setLat(place.getLat());
        recommend.setLng(place.getLng());
        recommend.setRating(place.getRating());
        recommend.setReviewCount(place.getReviewCount());
        recommend.setNaverUrl(placeService.getPlaceCard(place, null, null).getNaverUrl());
        recommend.setDistance((int) Math.round(place.getDistance()));
        recommend.setOpen(open.getOpen());
        recommend.setCloseAt(open.getCloseAt());
        recommend.setMinutesLeft(open.getMinutesLeft());
        recommend.setClosingSoon(Boolean.TRUE.equals(open.getClosingSoon()));
        recommend.setReasons(reasons);
        return recommend;
    }

    /** 카페·디저트면 40분, 아니면 60분 */
    private int getStay(String category) {
        boolean isCafe = category != null && (category.contains("카페") || category.contains("디저트") || category.contains("베이커리"));
        return isCafe ? CAFE_STAY_MIN : OTHER_STAY_MIN;
    }

    // ---------------------------------------------------------------- 일정에 넣기 도우미

    /** 추천 장소로 만든 새 칸 (id 없음 → 대안 적용이 createStop으로 저장) */
    private Stop toNewStop(Place place, int today) {
        Stop stop = new Stop();
        stop.setDay(today);
        stop.setPlaceId(place.getId());
        stop.setName(place.getName());
        stop.setCategory(place.getCategory());
        stop.setAddress(place.getAddress());
        stop.setLat(place.getLat());
        stop.setLng(place.getLng());
        stop.setStay(getStay(place.getCategory()));
        stop.setFixed(false);
        stop.setState("todo");
        return stop;
    }

    /** 고정 일정에 늦으면 그 바로 앞 칸(고정 아님)의 머묾을 최대 절반까지 줄임. 줄였으면 그 칸 이름, 아니면 null */
    private String shortenBeforeFixed(List<Stop> plan, Alternative result) {
        Stop fixedStop = liveStopFinder.findFixedStop(plan, null);
        if (fixedStop == null || result.getSlackMin() >= 0) {
            return null;
        }
        int fixedIndex = plan.indexOf(fixedStop);
        if (fixedIndex < 1 || Boolean.TRUE.equals(plan.get(fixedIndex - 1).getFixed())) {
            return null;
        }
        Stop before = plan.get(fixedIndex - 1);
        int stay = liveStopFinder.getStay(before);
        int cut = Math.min(-result.getSlackMin(), stay / 2);
        if (cut <= 0) {
            return null;
        }
        before.setStay(stay - cut);
        return before.getName();
    }

    private ApplyRequest toApplyRequest(Alternative result, InsertRequest request) {
        ApplyRequest apply = new ApplyRequest();
        apply.setKey("insert");
        apply.setStops(result.getStops());
        apply.setLat(request.getLat());
        apply.setLng(request.getLng());
        return apply;
    }

    /** "카페OO을(를) 다음 일정으로 넣었어요. (청계천 시간을 줄여) 세종문화회관에는 12분 여유가 있어요." */
    private String getInsertMessage(Place place, List<Stop> plan, Alternative result, String shortened) {
        String message = place.getName() + "을(를) 다음 일정으로 넣었어요. ";
        if (shortened != null) {
            message += shortened + " 시간을 줄여 ";
        }
        Stop fixedStop = liveStopFinder.findFixedStop(plan, null);
        if (fixedStop == null) {
            return message + "이후 일정 시각을 다시 계산했어요.";
        }
        if (result.getSlackMin() >= 0) {
            return message + fixedStop.getName() + "에는 " + result.getSlackMin() + "분 여유가 있어요.";
        }
        return message + "그래도 " + fixedStop.getName() + "에 " + (-result.getSlackMin()) + "분 늦어요. '대안 보기'를 확인하세요.";
    }
}
