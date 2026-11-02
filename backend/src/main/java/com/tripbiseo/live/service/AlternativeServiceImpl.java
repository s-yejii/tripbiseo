package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.WeatherClient;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.AppliedStop;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

/**
 * ④ 대안 A·B·C 만들기·적용 (POST /live/alternatives, /live/apply). LiveServiceImpl이 너무 커져서 나눔.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/LiveService.java (alternatives/fit/chain/substitute/apply)
 * 칸 고르기는 LiveStopFinder, 몇 시 도착 계산은 AlternativeCalculator가 해요.
 */
@Service
@RequiredArgsConstructor
public class AlternativeServiceImpl implements AlternativeService {

    private static final int TRIM_STAY_DIVIDE = 2;  // A 시간 단축: 한 칸에서 최대 이만큼 나눈 만큼까지 줄임 (절반)
    private static final int TRIM_BUFFER_MIN = 5;   // A 시간 단축: 늦는 시간보다 이만큼 더 줄여서 여유를 둠
    // B 한 곳 빼기: 뺄 순위 (클수록 먼저 뺌)
    private static final int DROP_RANK_CLOSED = 3;  // 도착하면 문 닫은 곳
    private static final int DROP_RANK_MISSED = 2;  // 계획 시간이 지난 곳
    private static final int DROP_RANK_OUTDOOR = 1; // 실내를 원할 때 야외 장소
    private static final int DROP_RANK_NORMAL = 0;  // 그 밖 (그중 오래 머무는 곳)
    private static final int SWAP_RADIUS_M = 1500;  // C 장소 바꾸기: 뺄 곳에서 이 반경(m) 안에서 찾음
    private static final String[] ALTERNATIVE_KEYS = {"A", "B", "C"}; // 대안 이름표 (만들어진 순서대로)
    private static final String[] INDOOR_WORDS = {"비", "실내", "추워", "더워", "날씨"}; // 요청에 있으면 실내 위주

    private final TripService tripService;                     // 오늘 칸 읽기·고치기 (②)
    private final PlaceService placeService;                   // 근처 장소·영업 정보·실내 판단 (②)
    private final WeatherClient weatherClient;                 // TODO: ④ 대안에서 비 오는지
    private final AiService aiService;                         // TODO: ④ Gemini 대안
    private final LiveStopFinder liveStopFinder;               // 오늘 칸·지금 칸·남은 칸 고르기 (④ 도우미)
    private final AlternativeCalculator alternativeCalculator; // 바뀐 칸 목록으로 몇 시 도착·여유 계산 (④ 도우미)

    /** 대안 A·B·C */
    // TODO: ④ (여유) aiService.isReady()면 askJsonWithWeather로 Gemini 대안, 실패하면 규칙만.
    //       주의: 예전 프롬프트의 "위치 (위도, 경도)"는 빼기. 날씨 도구는 인자 없음. 예전 askWithSearch(구글 검색 대체)는 쓰지 않기.
    @Override
    public AlternativesResponse makeAlternatives(long memberId, long tripId, AlternativesRequest request) {
        if (request.getLat() == null || request.getLng() == null) {
            throw fail(400, "내 위치를 먼저 찾아 주세요.");
        }
        // 1. 남은 일정 꺼내기
        Trip trip = tripService.getTrip(memberId, tripId);
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        List<Stop> remainingStops = getRemainingStops(todayStops, current);
        if (remainingStops.isEmpty()) {
            throw fail(400, "오늘 남은 일정이 없어요.");
        }

        // 2. 대안 A·B·C 만들기
        List<Alternative> alternatives = makeRuleAlternatives(trip, current, remainingStops, request);

        // 3. 마무리: 영업 정보·닫힌 곳 수 → 이름표 A·B·C → 추천 하나
        for (Alternative alternative : alternatives) {
            fillOpenInfo(alternative);
        }
        setKeys(alternatives);
        chooseRecommended(alternatives);

        AlternativesResponse response = new AlternativesResponse();
        response.setTitle(getTitle(remainingStops));
        response.setSubtitle("지금 위치·시각에서 다시 계산했어요");
        response.setSource("rule");
        response.setAlternatives(alternatives);
        return response;
    }

    // ---------------------------------------------------------------- 마무리 (6단계)

    /** 대안의 칸마다 도착 시각 기준 영업 정보(닫는 시각·닫힌 이유)를 채우고, 도착할 때 닫힌 곳 수를 셈 */
    private void fillOpenInfo(Alternative alternative) {
        int closedCount = 0;
        for (AltStop altStop : alternative.getStops()) {
            int stay = altStop.getStay() == null ? 0 : altStop.getStay();
            OpenInfo open = findOpenInfo(altStop.getPlaceId(), Geo.toMinutes(altStop.getEta()), stay);
            if (open == null) {
                continue; // 영업 정보를 모름
            }
            altStop.setCloseAt(open.getCloseAt());
            altStop.setClosedReason(open.getClosedReason());
            altStop.setClosingSoon(Boolean.TRUE.equals(open.getClosingSoon()));
            if (Boolean.FALSE.equals(open.getOpen())) {
                closedCount++;
            }
        }
        alternative.setClosedCount(closedCount);
    }

    /** 만들어진 순서대로 key A·B·C (C가 없으면 A·B만) */
    private void setKeys(List<Alternative> alternatives) {
        for (int i = 0; i < alternatives.size(); i++) {
            alternatives.get(i).setKey(ALTERNATIVE_KEYS[i]);
        }
    }

    /** 추천 대안 하나: 안 늦고 닫힌 곳 없는 대안 중 바뀐 칸이 가장 적은 것. 없으면 여유가 가장 많은 것 */
    private void chooseRecommended(List<Alternative> alternatives) {
        // 1번째: 좋은 대안(안 늦음 + 닫힌 곳 없음) 중 바뀐 칸이 가장 적은 것. 같으면 먼저 나온 것
        Alternative best = null;
        for (Alternative alternative : alternatives) {
            boolean isGood = alternative.getSlackMin() >= 0 && alternative.getClosedCount() == 0;
            if (isGood && (best == null || alternative.getChanges() < best.getChanges())) {
                best = alternative;
            }
        }
        // 2번째: 좋은 대안이 없으면 덜 늦는 것 (여유가 가장 많은 것)
        if (best == null) {
            for (Alternative alternative : alternatives) {
                if (best == null || alternative.getSlackMin() > best.getSlackMin()) {
                    best = alternative;
                }
            }
        }
        if (best != null) { // A는 항상 만들어져서 실제로 null일 일은 없지만, 목록이 비어 있어도 터지지 않게
            best.setRecommended(true);
        }
    }

    /** 화면 제목: 남은 고정 일정이 있으면 "{고정 일정}에 늦지 않는 대안", 없으면 "남은 일정 대안" */
    private String getTitle(List<Stop> remainingStops) {
        Stop fixedStop = liveStopFinder.findFixedStop(remainingStops, null);
        if (fixedStop == null) {
            return "남은 일정 대안";
        }
        return fixedStop.getName() + "에 늦지 않는 대안";
    }

    /** 규칙 대안 A·B·C (만들 수 없는 대안은 빠짐). B와 C는 같은 "뺄 곳"을 씀: B는 빼고, C는 다른 곳으로 바꿈 */
    private List<Alternative> makeRuleAlternatives(Trip trip, Stop current, List<Stop> remainingStops, AlternativesRequest request) {
        double lat = request.getLat();
        double lng = request.getLng();
        boolean isIndoorWanted = isIndoorRequest(request.getRequest());
        List<Alternative> alternatives = new ArrayList<>();
        alternatives.add(makeTrimAlternative(trip, current, remainingStops, lat, lng));

        Stop dropStop = findDropStop(remainingStops, isIndoorWanted);
        if (dropStop == null) {
            return alternatives; // 고정 일정만 남아서 뺄 곳·바꿀 곳이 없음
        }
        Alternative dropAlternative = makeDropAlternative(trip, current, remainingStops, lat, lng, dropStop, isIndoorWanted);
        if (dropAlternative != null) {
            alternatives.add(dropAlternative);
        }
        Alternative swapAlternative = makeSwapAlternative(trip, current, remainingStops, lat, lng, dropStop, isIndoorWanted);
        if (swapAlternative != null) {
            alternatives.add(swapAlternative);
        }
        return alternatives;
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
        List<Stop> trimmedStops = alternativeCalculator.copyStops(remainingStops);
        Alternative alternative = alternativeCalculator.calculate(trip, current, trimmedStops, lat, lng);

        // 2. 고정 일정에 늦으면 → 늦는 만큼 + 5분 줄이고 다시 계산
        boolean hasFixed = liveStopFinder.findFixedStop(remainingStops, null) != null;
        if (hasFixed && alternative.getSlackMin() < 0) {
            trimStays(trimmedStops, -alternative.getSlackMin() + TRIM_BUFFER_MIN);
            alternative = alternativeCalculator.calculate(trip, current, trimmedStops, lat, lng);
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

    /** B 한 곳 빼기: dropStop을 빼고 나머지는 계획대로. 빼면 남는 곳이 없으면 null */
    private Alternative makeDropAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng,
                                            Stop dropStop, boolean isIndoorWanted) {
        // 1. 빼면 남는 곳이 없으면 B를 만들지 않음
        if (remainingStops.size() == 1) {
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
        Alternative alternative = alternativeCalculator.calculate(trip, current, keptStops, lat, lng);

        // 4. 화면에 보일 이름 채우기
        alternative.setChanges(1);
        alternative.setShortName("한 곳 빼기");
        alternative.setTitle(dropStop.getName() + " 빼기");
        alternative.setReason(getDropReason(getDropRank(dropStop, isIndoorWanted)));
        return alternative;
    }

    /** C 장소 바꾸기: dropStop을 근처에서 그 시간에 영업 중인 평점 높은 곳으로 바꿈. 바꿀 곳이 없으면 null */
    private Alternative makeSwapAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng,
                                            Stop dropStop, boolean isIndoorWanted) {
        // 1. 바꿀 장소 찾기
        Place newPlace = findSwapPlace(dropStop, remainingStops, isIndoorWanted);
        if (newPlace == null) {
            return null;
        }

        // 2. 뺄 곳 자리에 새 장소를 넣은 목록 (나머지는 그대로라 복사하지 않음)
        List<Stop> swappedStops = new ArrayList<>();
        for (Stop stop : remainingStops) {
            if (stop == dropStop) {
                swappedStops.add(toNewStop(newPlace, dropStop));
            } else {
                swappedStops.add(stop);
            }
        }

        // 3. 계산기에 넣기
        Alternative alternative = alternativeCalculator.calculate(trip, current, swappedStops, lat, lng);

        // 4. 새 장소 칸에 "새로 추가" 적고, 화면에 보일 이름 채우기
        int index = remainingStops.indexOf(dropStop);
        alternative.getStops().get(index).setChange("새로 추가");
        alternative.setChanges(1);
        alternative.setShortName("장소 변경");
        alternative.setTitle(dropStop.getName() + " 대신 " + newPlace.getName());
        alternative.setReason((isIndoorWanted ? "실내" : "가까운") + " 평점 좋은 곳(그 시간에 영업 중)으로 바꿔요.");
        return alternative;
    }

    /** 바꿀 장소: dropStop 근처 1.5km 안에서 바꿀 수 있는 곳 중 평점이 가장 높은 곳 (없으면 null) */
    private Place findSwapPlace(Stop dropStop, List<Stop> remainingStops, boolean isIndoorWanted) {
        // getNearbyPlaceList: ②의 메서드, DB에서 PostGIS로 반경 안 장소를 가까운 순으로
        List<Place> candidates = placeService.getNearbyPlaceList(dropStop.getLat(), dropStop.getLng(), SWAP_RADIUS_M, null);
        int arrive = Math.max(Geo.toMinutes(dropStop.getTime()), Geo.nowMinutes());
        int stay = liveStopFinder.getStay(dropStop);
        Place bestPlace = null;
        for (Place place : candidates) {
            if (!isSwapCandidate(place, remainingStops, isIndoorWanted, arrive, stay)) {
                continue;
            }
            if (bestPlace == null || place.getRating() > bestPlace.getRating()) {
                bestPlace = place;
            }
        }
        return bestPlace;
    }

    /** 바꿀 수 있는 곳인지: 평점 있음, 오늘 남은 일정에 없음, (실내를 원하면) 실내, 도착할 때 영업 중 */
    private boolean isSwapCandidate(Place place, List<Stop> remainingStops, boolean isIndoorWanted, int arrive, int stay) {
        if (place.getRating() == null || isInStops(place, remainingStops)) {
            return false;
        }
        if (isIndoorWanted && !placeService.isIndoor(place.getCategory())) {
            return false;
        }
        OpenInfo open = placeService.getOpenInfo(place, Geo.now().toLocalDate(), arrive, stay);
        return !Boolean.FALSE.equals(open.getOpen()); // 영업시간을 모르면(null) 후보로 둠
    }

    /** 이 장소가 남은 일정에 이미 있나 (같은 이름이면 같은 곳으로 봄) */
    private boolean isInStops(Place place, List<Stop> stops) {
        for (Stop stop : stops) {
            if (place.getName().equals(stop.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 새 장소로 만든 칸: 시각·머묾은 원래 칸(oldStop) 그대로, id는 아직 없음(null) */
    private Stop toNewStop(Place place, Stop oldStop) {
        Stop stop = new Stop();
        stop.setTripId(oldStop.getTripId());
        stop.setDay(oldStop.getDay());
        stop.setPlaceId(place.getId());
        stop.setName(place.getName());
        stop.setCategory(place.getCategory());
        stop.setAddress(place.getAddress());
        stop.setLat(place.getLat());
        stop.setLng(place.getLng());
        stop.setTime(oldStop.getTime());
        stop.setStay(oldStop.getStay());
        stop.setFixed(false);
        stop.setState("todo");
        return stop;
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
        int arrive = Math.max(Geo.toMinutes(stop.getTime()), Geo.nowMinutes());
        OpenInfo open = findOpenInfo(stop.getPlaceId(), arrive, liveStopFinder.getStay(stop));
        return open != null && Boolean.FALSE.equals(open.getOpen());
    }

    /** 장소의 영업 정보 (arrive: 도착 시각(분), stay: 머무는 분). DB에 없는 장소·지워진 장소면 null */
    private OpenInfo findOpenInfo(String placeId, int arrive, int stay) {
        if (placeId == null) {
            return null; // DB에 없는 장소라 영업시간을 모름
        }
        try {
            Place place = placeService.getPlace(placeId);
            return placeService.getOpenInfo(place, Geo.now().toLocalDate(), arrive, stay);
        } catch (ApiException e) {
            return null; // 지워진 장소면 영업 정보 없이 계속
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

    /** 고른 대안을 일정에 반영 */
    // TODO: ④ 대안 적용. stops 없으면 400 "적용할 대안이 없어요." → id가 남은 칸이면 eta·stay로 updateStop(고정 일정 시각은 그대로),
    //       id가 없으면 createStop, 대안에 없는 남은 칸은 skipStops → 오늘 칸 tag(done/kept/changed/added). 참고: 예전 LiveService.apply/nextAlert.
    //       주의: ②의 쓰기 메서드를 여러 번 부르니 @Transactional 유지
    @Override
    @Transactional
    public ApplyResponse applyAlternative(long memberId, long tripId, ApplyRequest request) {
        if (request.getLat() == null || request.getLng() == null) {
            throw fail(400, "내 위치를 먼저 찾아 주세요.");
        }
        // 1. 남은 일정 꺼내기 (고른 대안과 비교할 원래 일정)
        Trip trip = tripService.getTrip(memberId, tripId);
        if (request.getStops() == null || request.getStops().isEmpty()) {
            throw fail(400, "적용할 대안이 없어요.");
        }
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        List<Stop> remainingStops = getRemainingStops(todayStops, current);

        // TODO: ④ 2단계 칸마다 반영(updateStop·createStop·skipStops), 3단계 응답 만들기 (아래는 아직 가짜 값)
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
