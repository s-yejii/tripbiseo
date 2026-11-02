package com.tripbiseo.live.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AssistRequest;
import com.tripbiseo.live.dto.AssistResponse;
import com.tripbiseo.live.dto.InsertRequest;
import com.tripbiseo.live.dto.InsertResponse;
import com.tripbiseo.live.dto.RecommendPlace;
import com.tripbiseo.live.dto.RecommendRequest;
import com.tripbiseo.live.dto.RecommendResponse;
import com.tripbiseo.member.service.NotificationService;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 여행 중 채팅·추천·일정에 넣기. 지금은 docs/API.md 예시 같은 가짜 값이에요. 대안은 AlternativeServiceImpl.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (679줄 → 여기, StatusService, TrackService, AlternativeService로 나눔)
 * 공통 주의
 *  - 오늘 칸은 tripService.getTrip으로 읽고, 고칠 때도 TripService(createStop·updateStop·skipStops)로. 예전처럼 TripMapper를 직접 쓰지 않기
 *  - 장소 판단은 placeService 메서드로 (예전 PlaceService.openInfo·isIndoor static 호출 대신)
 *  - Gemini 프롬프트에 좌표(위도·경도)를 넣지 않기. 날씨는 aiService.askJsonWithWeather(인자 없는 도구, 위치는 서버만 앎)
 */
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private final TripService tripService;                 // TODO: ④ 일정에 넣기
    private final PlaceService placeService;               // TODO: ④ 근처 장소·영업 정보
    private final AiService aiService;                     // TODO: ④ assist 분류
    private final NotificationService notificationService; // TODO: ④ 지연·출발 알림을 ①의 알림함에도 남길 때
    private final AlternativeService alternativeService;   // 채팅에서 "일정 바꿔줘"면 대안 만들기 (④)

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
            response.setData(alternativeService.makeAlternatives(memberId, tripId, alternativesRequest));
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
}
