package com.tripbiseo.live.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.AltStop;
import com.tripbiseo.live.dto.Alternative;
import com.tripbiseo.live.dto.AlternativesRequest;
import com.tripbiseo.live.dto.AlternativesResponse;
import com.tripbiseo.live.dto.AppliedStop;
import com.tripbiseo.live.dto.ApplyRequest;
import com.tripbiseo.live.dto.ApplyResponse;
import com.tripbiseo.live.dto.PreviewRequest;
import com.tripbiseo.live.dto.SwapCandidate;
import com.tripbiseo.member.dto.Notification;
import com.tripbiseo.member.service.NotificationService;
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
    private static final int MUST_MIN_STAY_MIN = 20; // A 시간 단축: 꼭 갈 곳은 여기까지만 줄임
    private static final int TRIM_BUFFER_MIN = 5;   // A 시간 단축: 늦는 시간보다 이만큼 더 줄여서 여유를 둠
    // B 한 곳 빼기: 뺄 순위 (클수록 먼저 뺌)
    private static final int DROP_RANK_CLOSED = 3;  // 도착하면 문 닫은 곳
    private static final int DROP_RANK_MISSED = 2;  // 계획 시간이 지난 곳
    private static final int DROP_RANK_OPTIONAL = 1; // 중요도 "빼도 됨"
    private static final int DROP_RANK_NORMAL = 0;  // 그 밖 (그중 오래 머무는 곳)
    private static final String IMPORTANCE_MUST = "must";     // 꼭 갈 곳: 자동으로 빼지 않음 (머묾은 20분까지 줄일 수 있음)
    private static final String IMPORTANCE_OPTIONAL = "drop"; // 빼도 되는 곳: 먼저 뺌
    private static final int SWAP_CANDIDATE_COUNT = 10;       // 장소 바꾸기 후보 수
    private static final int PREVIEW_PRECISE_COUNT = 3;       // 직접 고친 일정: 여행 중 화면(StatusServiceImpl)과 같게 앞 3구간은 진짜 길찾기
    private static final int SWAP_RADIUS_M = 1500;  // C 장소 바꾸기: 뺄 곳에서 이 반경(m) 안에서 찾음
    private static final int DEFAULT_STAY_MIN = 60;   // 대안 적용: 머무는 시간이 없으면 60분 (API.md)
    private static final int NEW_STOP_START_MIN = 30; // 대안 적용: 새 칸에 시각이 없으면 지금부터 30분 뒤
    private static final int LEAVE_ALERT_BEFORE_MIN = 10; // 대안 적용: 출발 이만큼 전부터 알림
    private static final String[] ALTERNATIVE_KEYS = {"A", "B", "C"}; // 대안 이름표 (만들어진 순서대로)
    private static final String RAIN_SHORT_NAME = "비 피하기"; // 비 오기 전에 야외 먼저 (11/06)
    private static final int MEMO_MAX_LENGTH = 200;          // trip_stop.memo 저장 규칙

    private final TripService tripService;                     // 오늘 칸 읽기·고치기 (②)
    private final PlaceService placeService;                   // 근처 장소·영업 정보 (②)
    private final LiveStopFinder liveStopFinder;               // 오늘 칸·지금 칸·남은 칸 고르기 (④ 도우미)
    private final LiveTripReader liveTripReader;               // 공통 오류 검사 + 진행 중인 여행 (④ 도우미)
    private final AlternativeCalculator alternativeCalculator; // 바뀐 칸 목록으로 몇 시 도착·여유 계산 (④ 도우미)
    private final NotificationService notificationService;     // 적용했다는 알림 남기기 (①)
    private final LiveWeatherChecker liveWeatherChecker;       // 오늘 비 시작 시각 (④ 도우미, 11/06)
    private final TransferFinder transferFinder;               // 고정 일정까지 더 빨리 가는 방법 (④ 도우미, 11/06)

    /** 대안 A·B·C */
    // 규칙 대안만 만들어요. 비 올 때 실내로 바꾸는 대안은 만들지 않음 (11/02 결정: 비는 알림만)
    // 11/06: 대신 "비 오기 전에 야외 먼저"(순서만 바꿈)는 만들어요 (돌발 ②, 김주형 요청)
    // TODO: ④ (여유) Gemini 대안 — aiService.askJson으로 규칙 대안을 다듬기. 좌표는 프롬프트에 넣지 않기
    @Override
    public AlternativesResponse makeAlternatives(long memberId, long tripId, AlternativesRequest request) {
        // 1. 공통 오류 검사 후 남은 일정 꺼내기
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
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
        //        단, 비 피하기(맨 앞)가 좋은 대안이면 그걸 추천 (비 소식 때문에 다시 짜는 거라서, 11/06)
        Alternative best = null;
        for (Alternative alternative : alternatives) {
            boolean isGood = alternative.getSlackMin() >= 0 && alternative.getClosedCount() == 0;
            if (isGood && RAIN_SHORT_NAME.equals(alternative.getShortName())) {
                best = alternative;
                break;
            }
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
        List<Alternative> alternatives = new ArrayList<>();
        if (remainingStops.size() == 1 && Boolean.TRUE.equals(remainingStops.get(0).getFixed())) {
            return makeGoNowAlternatives(trip, current, remainingStops, lat, lng); // 남은 곳이 예약(KTX)뿐이면 줄이거나 뺄 곳이 없어서 "빨리 가는 방법"만 (11/08)
        }
        Alternative rainAlternative = makeRainAlternative(trip, current, remainingStops, lat, lng);
        if (rainAlternative != null) {
            alternatives.add(rainAlternative); // 비 소식이 있으면 맨 앞에 (11/06)
        }
        alternatives.add(makeTrimAlternative(trip, current, remainingStops, lat, lng));
        Alternative transferAlternative = makeTransferAlternative(trip, current, remainingStops, lat, lng);
        if (transferAlternative != null) {
            alternatives.add(transferAlternative); // 고정 일정에 늦을 때 갈아타기 (11/06)
        }

        Stop dropStop = findDropStop(remainingStops);
        if (dropStop == null) {
            return alternatives; // 고정 일정만 남아서 뺄 곳·바꿀 곳이 없음
        }
        Alternative dropAlternative = makeDropAlternative(trip, current, remainingStops, lat, lng, dropStop);
        if (dropAlternative != null && alternatives.size() < ALTERNATIVE_KEYS.length) {
            alternatives.add(dropAlternative);
        }
        Alternative swapAlternative = makeSwapAlternative(trip, current, remainingStops, lat, lng, dropStop);
        if (swapAlternative != null && alternatives.size() < ALTERNATIVE_KEYS.length) {
            alternatives.add(swapAlternative); // 대안은 3개까지 (비 피하기가 있으면 장소 바꾸기는 빠짐)
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

    /**
     * 갈아타기 (돌발 ③, 11/06): 가는 곳·머묾은 그대로, 고정 일정(KTX)으로 가는 마지막 구간만 더 빠른 방법으로.
     * 그대로 가면 늦을 때만 만들고, 실제 길찾기로 "지하철로 ○○역까지 → 택시"나 "택시로 바로"가 더 빠를 때만. 아니면 null
     */
    private Alternative makeTransferAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng) {
        Alternative alternative = alternativeCalculator.calculate(trip, current, alternativeCalculator.copyStops(remainingStops), lat, lng);
        int fixedIndex = findFirstFixedIndex(remainingStops);
        if (fixedIndex < 0 || alternative.getSlackMin() >= 0) {
            return null; // 고정 일정이 없거나 안 늦음
        }
        // 마지막 구간의 출발점·출발 시각: 앞 칸이 있으면 그 칸을 떠날 때, 없으면 지금 위치에서 지금
        double fromLat = lat;
        double fromLng = lng;
        int leaveAt = Geo.nowMinutes();
        if (fixedIndex > 0) {
            AltStop before = alternative.getStops().get(fixedIndex - 1);
            fromLat = before.getLat();
            fromLng = before.getLng();
            leaveAt = Geo.toMinutes(before.getEta()) + (before.getStay() == null ? 0 : before.getStay());
        }
        Stop fixedStop = remainingStops.get(fixedIndex);
        TransferFinder.Option option = transferFinder.findFaster(fromLat, fromLng, fixedStop.getLat(), fixedStop.getLng());
        if (option == null) {
            return null;
        }
        int arrive = leaveAt + option.getMinutes();
        AltStop fixedAltStop = alternative.getStops().get(fixedIndex);
        fixedAltStop.setMoveMin(option.getMinutes());
        fixedAltStop.setChange(option.getText());
        alternative.setFinalArrive(Geo.toHhmm(arrive));
        alternative.setSlackMin(Geo.toMinutes(fixedStop.getTime()) - arrive);
        alternative.setChanges(1);
        alternative.setShortName("갈아타기");
        alternative.setTitle(option.getTitle());
        alternative.setReason(fixedStop.getName() + "까지 대중교통 " + option.getTransitMinutes() + "분 대신 " + option.getText() + "로 가요.");
        return alternative;
    }

    /**
     * 남은 곳이 예약(예: 서울역 KTX) 하나뿐일 때: 지금 바로 가는 방법 두 가지 (11/08 시연 돌발: 타임스퀘어에서 늦어짐).
     * A 지하철(대중교통)로 바로 / B 택시로 바로. 둘 다 실제 길찾기 값, 예약을 빼는 대안은 만들지 않음
     */
    private List<Alternative> makeGoNowAlternatives(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng) {
        List<Alternative> alternatives = new ArrayList<>();
        Stop fixedStop = remainingStops.get(0);
        alternatives.add(makeGoNowAlternative(trip, current, remainingStops, lat, lng, "transit", "지하철로 바로 가기",
                fixedStop.getName() + "까지 지하철로 지금 바로 가요. 퇴근길엔 막히지 않아요."));
        alternatives.add(makeGoNowAlternative(trip, current, remainingStops, lat, lng, "taxi", "택시로 바로 가기",
                fixedStop.getName() + "까지 택시로 바로 가요. 퇴근길이라 막힐 수 있어요."));
        return alternatives;
    }

    private Alternative makeGoNowAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng,
                                             String mode, String title, String reason) {
        List<Stop> stops = alternativeCalculator.copyStops(remainingStops);
        stops.get(0).setMode(mode);
        Alternative alternative = alternativeCalculator.calculate(trip, current, stops, lat, lng);
        alternative.setChanges(1);
        alternative.setShortName("transit".equals(mode) ? "지하철" : "택시");
        alternative.setTitle(title);
        alternative.setReason(reason);
        return alternative;
    }

    /** 남은 칸 중 첫 고정 일정의 위치 (없으면 -1) */
    private int findFirstFixedIndex(List<Stop> stops) {
        for (int i = 0; i < stops.size(); i++) {
            if (Boolean.TRUE.equals(stops.get(i).getFixed())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 비 피하기 (돌발 ②, 11/06): 오늘 비가 오면, 비 시작 뒤까지 있을 야외 칸을 남은 일정 맨 앞으로 옮김.
     * 가는 곳·머무는 시간은 그대로, 순서만 바꿈. 고정 일정은 옮기지 않음. 비 소식이나 옮길 칸이 없으면 null
     */
    private Alternative makeRainAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng) {
        Integer rainStart = liveWeatherChecker.findRainStart(lat, lng);
        if (rainStart == null) {
            return null;
        }
        List<Stop> outdoorStops = new ArrayList<>();
        List<Stop> otherStops = new ArrayList<>();
        for (Stop stop : remainingStops) {
            if (isWetStop(stop, rainStart)) {
                outdoorStops.add(stop);
            } else {
                otherStops.add(stop);
            }
        }
        if (outdoorStops.isEmpty() || remainingStops.get(0) == outdoorStops.get(0)) {
            return null; // 옮길 야외 칸이 없거나, 이미 맨 앞
        }
        List<Stop> reordered = new ArrayList<>(outdoorStops);
        reordered.addAll(otherStops);
        Alternative alternative = alternativeCalculator.calculate(trip, current, alternativeCalculator.copyStops(reordered), lat, lng);
        for (int i = 0; i < outdoorStops.size(); i++) {
            alternative.getStops().get(i).setChange("비 오기 전에 먼저");
        }
        String names = outdoorStops.get(0).getName() + (outdoorStops.size() > 1 ? " 외 " + (outdoorStops.size() - 1) + "곳" : "");
        alternative.setChanges(outdoorStops.size());
        alternative.setShortName(RAIN_SHORT_NAME);
        alternative.setTitle(names + " 먼저 가기");
        alternative.setReason(Geo.toHhmm(rainStart) + "부터 비 소식이 있어서, 야외인 " + names + "을(를) 비 오기 전에 먼저 들러요.");
        return alternative;
    }

    /** 비를 맞을 칸인지: 고정 일정이 아니고, 실내가 아니고, 계획대로면 비 시작 뒤에도 거기 있음 */
    private boolean isWetStop(Stop stop, int rainStart) {
        if (Boolean.TRUE.equals(stop.getFixed()) || placeService.isIndoor(stop.getCategory()) || stop.getTime() == null) {
            return false;
        }
        return Geo.toMinutes(stop.getTime()) + liveStopFinder.getStay(stop) > rainStart;
    }

    /** B 한 곳 빼기: dropStop을 빼고 나머지는 계획대로. 빼면 남는 곳이 없으면 null */
    private Alternative makeDropAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng,
                                            Stop dropStop) {
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
        alternative.setReason(getDropReason(getDropRank(dropStop)));
        return alternative;
    }

    /** C 장소 바꾸기: dropStop을 근처에서 그 시간에 영업 중인 평점 높은 곳으로 바꿈. 바꿀 곳이 없으면 null */
    private Alternative makeSwapAlternative(Trip trip, Stop current, List<Stop> remainingStops, double lat, double lng,
                                            Stop dropStop) {
        // 1. 바꿀 장소 찾기
        Place newPlace = findSwapPlace(dropStop, remainingStops);
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
        alternative.setReason("가까운 평점 좋은 곳(그 시간에 영업 중)으로 바꿔요.");
        return alternative;
    }

    /** 바꿀 장소: dropStop 근처 1.5km 안에서 바꿀 수 있는 곳 중 평점이 가장 높은 곳 (없으면 null) */
    private Place findSwapPlace(Stop dropStop, List<Stop> remainingStops) {
        // getNearbyPlaceList: ②의 메서드, DB에서 PostGIS로 반경 안 장소를 가까운 순으로
        List<Place> candidates = placeService.getNearbyPlaceList(dropStop.getLat(), dropStop.getLng(), SWAP_RADIUS_M, null);
        int arrive = Math.max(Geo.toMinutes(dropStop.getTime()), Geo.nowMinutes());
        int stay = liveStopFinder.getStay(dropStop);
        Place bestPlace = null;
        for (Place place : candidates) {
            if (!isSwapCandidate(place, remainingStops, arrive, stay)) {
                continue;
            }
            if (bestPlace == null || place.getRating() > bestPlace.getRating()) {
                bestPlace = place;
            }
        }
        return bestPlace;
    }

    /** 바꿀 수 있는 곳인지: 평점 있음, 오늘 남은 일정에 없음, 도착할 때 영업 중 */
    private boolean isSwapCandidate(Place place, List<Stop> remainingStops, int arrive, int stay) {
        if (place.getRating() == null || isInStops(place, remainingStops)) {
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
        stop.setImportance(oldStop.getImportance());
        return stop;
    }

    /** 뺄 곳 하나: 고정 일정이 아닌 칸 중 뺄 순위가 가장 높은 곳, 같으면 오래 머무는 곳 (없으면 null) */
    private Stop findDropStop(List<Stop> stops) {
        Stop dropStop = null;
        int dropRank = -1;
        for (Stop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed()) || LiveStopFinder.isStay(stop)) {
                continue; // 고정 일정·숙소는 빼지 않음 (11/08)
            }
            if (isMust(stop)) {
                continue; // 꼭 갈 곳은 빼지 않음
            }
            int rank = getDropRank(stop);
            boolean isHigherRank = rank > dropRank;
            boolean isLongerStay = rank == dropRank && liveStopFinder.getStay(stop) > liveStopFinder.getStay(dropStop);
            if (isHigherRank || isLongerStay) {
                dropStop = stop;
                dropRank = rank;
            }
        }
        return dropStop;
    }

    /** 뺄 순위: 문 닫은 곳 > 계획 시간 지난 곳 > 그 밖 */
    private int getDropRank(Stop stop) {
        if (isClosedAtArrival(stop)) {
            return DROP_RANK_CLOSED;
        }
        if (liveStopFinder.isMissed(stop)) {
            return DROP_RANK_MISSED;
        }
        if (IMPORTANCE_OPTIONAL.equals(stop.getImportance())) {
            return DROP_RANK_OPTIONAL;
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
        if (rank == DROP_RANK_OPTIONAL) {
            return "빼도 된다고 한 곳을 빼요.";
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

    /**
     * 고정 일정 앞 칸부터 차례로 줄여서 need분을 채움. 먼저 꼭 갈 곳이 아닌 칸을 최대 절반까지,
     * 그래도 모자라면 꼭 갈 곳도 20분까지 줄임 (꼭 = 빼지 않는다는 뜻. 11/04 사용자 요청으로 "머묾도 그대로"에서 바뀜)
     */
    private void trimStays(List<Stop> stops, int need) {
        int left = need;
        for (Stop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed()) || left <= 0) {
                break; // 고정 일정 뒤 칸은 늦는 것과 상관없어서 안 줄임
            }
            if (!isMust(stop)) {
                int stay = liveStopFinder.getStay(stop);
                int cut = Math.min(stay / TRIM_STAY_DIVIDE, left);
                stop.setStay(stay - cut);
                left -= cut;
            }
        }
        for (Stop stop : stops) {
            if (Boolean.TRUE.equals(stop.getFixed()) || left <= 0) {
                break;
            }
            if (isMust(stop)) {
                int stay = liveStopFinder.getStay(stop);
                int cut = Math.min(Math.max(0, stay - MUST_MIN_STAY_MIN), left);
                stop.setStay(stay - cut);
                left -= cut;
            }
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
    // ②의 쓰기 메서드(createStop·updateStop·skipStops)를 여러 번 부르니, 하나라도 실패하면 전부 되돌리게 @Transactional
    @Override
    @Transactional
    public ApplyResponse applyAlternative(long memberId, long tripId, ApplyRequest request) {
        // 1. 공통 오류 검사 후 남은 일정 꺼내기 (고른 대안과 비교할 원래 일정)
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        if (request.getStops() == null || request.getStops().isEmpty()) {
            throw fail(400, "적용할 대안이 없어요.");
        }
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        List<Stop> remainingStops = getRemainingStops(todayStops, current);

        // 2. 칸마다 ②에게 부탁: 남은 칸은 수정, 새 장소는 추가, 대안에 없는 남은 칸은 빼기
        Map<Long, String> noteMap = new HashMap<>(); // 칸 id → "60분 → 30분" (3단계 응답에 씀)
        List<Long> keptIds = applyChosenStops(memberId, tripId, trip, request.getStops(), remainingStops, noteMap);
        skipUnchosenStops(memberId, tripId, remainingStops, keptIds);

        // 3. 바뀐 일정을 다시 읽어서 응답 만들기
        List<Stop> updatedTodayStops = liveStopFinder.getTodayStops(tripService.getTrip(memberId, tripId));
        ApplyResponse response = new ApplyResponse();
        response.setKey(request.getKey());
        response.setMessage(getApplyMessage(remainingStops));
        response.setNextAlert(getNextAlert(updatedTodayStops));
        response.setStops(toAppliedStops(updatedTodayStops, todayStops, current, noteMap));
        sendApplyNotice(memberId, tripId, response);
        return response;
    }

    /** 대안을 적용했다는 알림 (적용할 때마다 하나씩 쌓임) */
    private void sendApplyNotice(long memberId, long tripId, ApplyResponse response) {
        Notification notice = new Notification();
        notice.setMemberId(memberId);
        notice.setKind("trip");
        notice.setLevel("ok");
        notice.setTitle(response.getMessage());    // 예: 공연장에 늦지 않게 일정을 바꿨어요!
        notice.setText(response.getNextAlert());   // 예: ○○에서 출발할 시각(15:52) 10분 전부터 1분마다 알려드릴게요… (없으면 null)
        notice.setLink("/trips/today?id=" + tripId);
        notificationService.send(notice);
    }

    // ---------------------------------------------------------------- 직접 고친 일정 계산 · 바꿀 장소 후보

    /** 사용자가 고친 남은 일정을 계산만 함 (저장 안 함). 목록에 없는 남은 칸은 뺀 것으로 봄 */
    @Override
    public Alternative previewAlternative(long memberId, long tripId, PreviewRequest request) {
        // 1. 공통 오류 검사 후 남은 일정 꺼내기
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, request.getLat(), request.getLng());
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, request.getLat(), request.getLng());
        List<Stop> remainingStops = getRemainingStops(todayStops, current);

        // 2. 화면이 보낸 순서대로 계산할 칸 만들기
        List<Stop> chosenStops = toChosenStops(request.getStops(), remainingStops, trip);
        if (chosenStops.isEmpty()) {
            throw fail(400, "계산할 일정이 없어요.");
        }

        // 3. 계산 + 영업 정보 + 바뀐 곳 표시
        Alternative alternative = alternativeCalculator.calculate(trip, current, chosenStops, request.getLat(), request.getLng(), PREVIEW_PRECISE_COUNT);
        fillOpenInfo(alternative);
        alternative.setChanges(markPreviewChanges(alternative, remainingStops, chosenStops));
        alternative.setShortName("직접 고르기");
        alternative.setTitle("직접 고친 일정");
        alternative.setReason("고른 대로 다시 계산했어요.");
        return alternative;
    }

    /** 남은 칸 하나를 바꿀 장소 후보: 1.5km 안, 도착할 때 영업 중, 평점 있음, 일정에 없음. 가까운 순 최대 10곳 */
    @Override
    public List<SwapCandidate> getSwapCandidateList(long memberId, long tripId, long stopId, Double lat, Double lng) {
        // 1. 공통 오류 검사 후 바꿀 칸 찾기
        Trip trip = liveTripReader.getOngoingTrip(memberId, tripId, lat, lng);
        List<Stop> todayStops = liveStopFinder.getTodayStops(trip);
        Stop current = liveStopFinder.findCurrentStop(todayStops, lat, lng);
        List<Stop> remainingStops = getRemainingStops(todayStops, current);
        Stop swapStop = findStopById(remainingStops, stopId);
        if (swapStop == null) {
            throw fail(404, "남은 일정에서 찾을 수 없어요.");
        }
        if (Boolean.TRUE.equals(swapStop.getFixed())) {
            throw fail(400, "고정 일정은 바꿀 수 없어요.");
        }

        // 2. 근처 장소 중 바꿀 수 있는 곳마다 바꿨을 때 계산 (가까운 순이라 앞에서부터 10곳)
        List<Place> places = placeService.getNearbyPlaceList(swapStop.getLat(), swapStop.getLng(), SWAP_RADIUS_M, null);
        int arrive = Math.max(Geo.toMinutes(swapStop.getTime()), Geo.nowMinutes());
        int stay = liveStopFinder.getStay(swapStop);
        List<SwapCandidate> candidates = new ArrayList<>();
        for (Place place : places) {
            if (candidates.size() >= SWAP_CANDIDATE_COUNT) {
                break;
            }
            if (isSwapCandidate(place, remainingStops, arrive, stay)) {
                candidates.add(toSwapCandidate(trip, current, remainingStops, swapStop, place, lat, lng));
            }
        }
        return candidates;
    }

    /** 화면이 보낸 칸 → 계산할 칸 목록 (못 쓰는 칸은 건너뜀) */
    private List<Stop> toChosenStops(List<AltStop> requestStops, List<Stop> remainingStops, Trip trip) {
        List<Stop> chosenStops = new ArrayList<>();
        if (requestStops == null) {
            return chosenStops;
        }
        for (AltStop requested : requestStops) {
            Stop stop = toChosenStop(requested, remainingStops, trip);
            if (stop != null) {
                chosenStops.add(stop);
            }
        }
        return chosenStops;
    }

    /** id가 남은 칸이면 복사해서 머묾만 바꿈(고정 일정은 그대로), id가 없으면 새 장소 칸. 못 쓰면 null */
    private Stop toChosenStop(AltStop requested, List<Stop> remainingStops, Trip trip) {
        if (requested.getId() == null) {
            if (requested.getLat() == null || requested.getLng() == null) {
                return null; // 좌표 없는 새 장소는 계산할 수 없음
            }
            return toAddedStop(requested, liveStopFinder.getToday(trip));
        }
        Stop original = findStopById(remainingStops, requested.getId());
        if (original == null) {
            return null; // 다녀온 칸·남의 칸은 무시
        }
        Stop copy = alternativeCalculator.copyStops(List.of(original)).get(0);
        boolean isFixed = Boolean.TRUE.equals(original.getFixed());
        if (requested.getStay() != null && !isFixed) {
            copy.setStay(Math.max(0, requested.getStay()));
        }
        return copy;
    }

    /** 칸마다 "60분 → 30분" 또는 "새로 추가"를 적고, 바뀐 칸 수(뺀 칸 포함)를 돌려줌 */
    private int markPreviewChanges(Alternative alternative, List<Stop> remainingStops, List<Stop> chosenStops) {
        int keptCount = 0;
        int changes = 0;
        for (int i = 0; i < chosenStops.size(); i++) {
            Stop chosen = chosenStops.get(i);
            AltStop altStop = alternative.getStops().get(i);
            if (chosen.getId() == null) {
                altStop.setChange("새로 추가");
                changes++;
                continue;
            }
            keptCount++;
            int before = liveStopFinder.getStay(findStopById(remainingStops, chosen.getId()));
            int after = liveStopFinder.getStay(chosen);
            if (before != after) {
                altStop.setChange(before + "분 → " + after + "분");
                changes++;
            }
        }
        return changes + (remainingStops.size() - keptCount); // 뺀 칸도 바뀐 것으로 셈
    }

    /** swapStop을 place로 바꿨을 때 계산해서 후보 한 줄로 */
    private SwapCandidate toSwapCandidate(Trip trip, Stop current, List<Stop> remainingStops, Stop swapStop,
                                          Place place, double lat, double lng) {
        List<Stop> swappedStops = new ArrayList<>();
        for (Stop stop : remainingStops) {
            swappedStops.add(stop == swapStop ? toNewStop(place, swapStop) : stop);
        }
        Alternative alternative = alternativeCalculator.calculate(trip, current, swappedStops, lat, lng);
        AltStop newStop = alternative.getStops().get(remainingStops.indexOf(swapStop));

        SwapCandidate candidate = new SwapCandidate();
        candidate.setId(place.getId());
        candidate.setName(place.getName());
        candidate.setCategory(place.getCategory());
        candidate.setAddress(place.getAddress());
        candidate.setLat(place.getLat());
        candidate.setLng(place.getLng());
        candidate.setRating(place.getRating());
        candidate.setReviewCount(place.getReviewCount());
        candidate.setNaverUrl(place.getMapUrl() != null ? place.getMapUrl() : placeService.getNaverUrl(place.getName(), place.getAddress()));
        candidate.setDistance(place.getDistance() == null ? null : (int) Math.round(place.getDistance()));
        candidate.setMoveMin(newStop.getMoveMin());
        candidate.setEta(newStop.getEta());
        candidate.setFinalArrive(alternative.getFinalArrive());
        candidate.setSlackMin(alternative.getSlackMin());
        return candidate;
    }

    /** 중요도가 "꼭"인 칸인지 */
    private boolean isMust(Stop stop) {
        return IMPORTANCE_MUST.equals(stop.getImportance());
    }

    // ---------------------------------------------------------------- 대안 적용 도우미 (4번)

    /**
     * 고른 대안의 칸마다: id가 남은 칸이면 수정(updateStop), id가 없으면 새 칸 추가(createStop).
     * id가 있는데 남은 칸이 아니면(다녀온 칸·남의 칸) 무시. 대안에 남은 칸 id 목록을 돌려줌
     */
    private List<Long> applyChosenStops(long memberId, long tripId, Trip trip, List<AltStop> chosenStops,
                                        List<Stop> remainingStops, Map<Long, String> noteMap) {
        List<Long> keptIds = new ArrayList<>(); // 대안에 남은 칸 id (여기 없는 남은 칸은 나중에 뺌)
        for (AltStop chosen : chosenStops) {
            if (chosen.getId() == null) {
                addNewStop(memberId, tripId, trip, chosen); // 새 장소 → 넣어 줘
            } else {
                Stop original = findStopById(remainingStops, chosen.getId());
                if (original != null) {
                    updateChosenStop(memberId, tripId, original, chosen, noteMap); // 원래 칸 → 바꿔 줘
                    keptIds.add(original.getId());
                }
            }
        }
        return keptIds;
    }

    /** 고른 대안의 새 장소를 오늘 일정에 추가 (②의 createStop). 좌표가 없으면 건너뜀 (API.md) */
    private void addNewStop(long memberId, long tripId, Trip trip, AltStop chosen) {
        if (chosen.getLat() == null || chosen.getLng() == null) {
            return;
        }
        tripService.createStop(memberId, tripId, toAddedStop(chosen, liveStopFinder.getToday(trip)));
    }

    /** 남은 칸 하나를 고른 대안대로 수정: 머묾, 시각(고정 일정은 그대로). 머묾이 바뀌면 "60분 → 30분"을 noteMap에 */
    private void updateChosenStop(long memberId, long tripId, Stop original, AltStop chosen, Map<Long, String> noteMap) {
        int beforeStay = liveStopFinder.getStay(original);
        int afterStay = chosen.getStay() == null ? DEFAULT_STAY_MIN : chosen.getStay();
        boolean isFixed = Boolean.TRUE.equals(original.getFixed());

        Stop update = new Stop();
        update.setTime(isFixed || chosen.getEta() == null ? original.getTime() : chosen.getEta());
        update.setStay(afterStay);
        update.setMode(original.getMode()); // ②의 updateStop은 mode·memo를 보낸 값으로 덮어써서, 원래 값을 그대로 보냄
        update.setMemo(original.getMemo());
        if (isFixed && chosen.getChange() != null && chosen.getChange().contains("택시")) {
            update.setMemo(trimMemo(chosen.getChange())); // 갈아타기 대안: "지하철로 ○○역까지 → 택시"를 메모로 (11/06)
            update.setMode("taxi");
        }
        tripService.updateStop(memberId, tripId, original.getId(), update);

        if (beforeStay != afterStay) {
            noteMap.put(original.getId(), beforeStay + "분 → " + afterStay + "분");
        }
    }

    /** 메모는 200자까지 (trip_stop.memo) */
    private String trimMemo(String text) {
        return text.length() > MEMO_MAX_LENGTH ? text.substring(0, MEMO_MAX_LENGTH) : text;
    }

    /** 대안에 없는 남은 칸을 한 번에 skipped로 (②의 skipStops) */
    private void skipUnchosenStops(long memberId, long tripId, List<Stop> remainingStops, List<Long> keptIds) {
        List<Long> skipIds = new ArrayList<>();
        for (Stop stop : remainingStops) {
            if (!keptIds.contains(stop.getId())) {
                skipIds.add(stop.getId());
            }
        }
        if (!skipIds.isEmpty()) {
            tripService.skipStops(memberId, tripId, skipIds);
        }
    }

    /** 칸 목록에서 id가 같은 칸 (없으면 null) */
    private Stop findStopById(List<Stop> stops, Long id) {
        for (Stop stop : stops) {
            if (stop.getId().equals(id)) {
                return stop;
            }
        }
        return null;
    }

    /** 고른 대안의 새 장소 → 저장할 새 칸. 시각이 없으면 지금 + 30분, 머묾이 없으면 60분 */
    private Stop toAddedStop(AltStop chosen, int today) {
        Stop stop = new Stop();
        stop.setDay(today);
        stop.setPlaceId(chosen.getPlaceId());
        stop.setName(chosen.getName());
        stop.setCategory(chosen.getCategory());
        stop.setLat(chosen.getLat());
        stop.setLng(chosen.getLng());
        stop.setTime(chosen.getEta() == null ? Geo.toHhmm(Geo.nowMinutes() + NEW_STOP_START_MIN) : chosen.getEta());
        stop.setStay(chosen.getStay() == null ? DEFAULT_STAY_MIN : chosen.getStay());
        stop.setFixed(false);
        return stop;
    }

    // ---------------------------------------------------------------- 대안 적용 응답 (4번 3단계)

    /** 적용 후 안내 문구: 남은 고정 일정이 있으면 "{고정 일정}에 늦지 않게 일정을 바꿨어요!" */
    private String getApplyMessage(List<Stop> remainingStops) {
        Stop fixedStop = liveStopFinder.findFixedStop(remainingStops, null);
        if (fixedStop == null) {
            return "일정을 바꿨어요!";
        }
        return fixedStop.getName() + "에 늦지 않게 일정을 바꿨어요!";
    }

    /** 다음 출발 알림 안내: 아직 갈 칸(고정 일정 제외) 중 "출발 10분 전"이 아직 안 지난 첫 칸. 없으면 null */
    private String getNextAlert(List<Stop> todayStops) {
        int now = Geo.nowMinutes();
        for (Stop stop : todayStops) {
            boolean isTodoStop = liveStopFinder.isTodo(stop) && !Boolean.TRUE.equals(stop.getFixed());
            int leaveAt = liveStopFinder.getLeaveMinutes(stop); // 도착(기록 없으면 계획 시각) + 머묾
            if (isTodoStop && leaveAt - LEAVE_ALERT_BEFORE_MIN > now) {
                return stop.getName() + "에서 출발할 시각(" + Geo.toHhmm(leaveAt) + ") " + LEAVE_ALERT_BEFORE_MIN
                        + "분 전부터 1분마다 알려드릴게요. 늦게 도착하면 도착한 시각 기준으로 다시 맞춰요.";
            }
        }
        return null;
    }

    /** 바뀐 오늘 칸(뺀 칸 제외)마다 표시를 붙여 화면용으로. note: "60분 → 30분"·"새로 추가" */
    private List<AppliedStop> toAppliedStops(List<Stop> updatedTodayStops, List<Stop> beforeTodayStops, Stop current,
                                             Map<Long, String> noteMap) {
        List<AppliedStop> appliedStops = new ArrayList<>();
        for (Stop stop : updatedTodayStops) {
            if (!"skipped".equals(stop.getState())) {
                String tag = getApplyTag(stop, beforeTodayStops, current, noteMap);
                String note = "added".equals(tag) ? "새로 추가" : noteMap.get(stop.getId());
                appliedStops.add(toAppliedStop(stop, tag, note));
            }
        }
        return appliedStops;
    }

    /** 칸 표시: done(다녀옴·지금 있는 곳) > added(적용 전에 없던 칸) > changed(머묾이 바뀜) > kept(그대로) */
    private String getApplyTag(Stop stop, List<Stop> beforeTodayStops, Stop current, Map<Long, String> noteMap) {
        boolean isHere = current != null && current.getId().equals(stop.getId());
        if (!liveStopFinder.isTodo(stop) || isHere) {
            return "done";
        }
        if (findStopById(beforeTodayStops, stop.getId()) == null) {
            return "added";
        }
        if (noteMap.containsKey(stop.getId())) {
            return "changed";
        }
        return "kept";
    }

    /** Stop → 화면용 AppliedStop */
    private AppliedStop toAppliedStop(Stop stop, String tag, String note) {
        AppliedStop appliedStop = new AppliedStop();
        appliedStop.setId(stop.getId());
        appliedStop.setTime(stop.getTime());
        appliedStop.setName(stop.getName());
        appliedStop.setCategory(stop.getCategory());
        appliedStop.setStay(stop.getStay());
        appliedStop.setTag(tag);
        appliedStop.setNote(note);
        return appliedStop;
    }
}
