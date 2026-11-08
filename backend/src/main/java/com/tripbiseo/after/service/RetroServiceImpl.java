package com.tripbiseo.after.service;

import org.springframework.stereotype.Service;

import com.tripbiseo.after.dto.ExtraStop;
import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.dto.RetroStop;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.TrackPoint;
import com.tripbiseo.live.service.TrackService;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;
import com.tripbiseo.trip.service.TripService;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RetroServiceImpl implements RetroService {
    
    private static final int STAY_RADIUS_M = 100;       // 이 반경 안이면 "한 자리" (GPS는 가만히 있어도 20~50m 흔들림)
    private static final int STAY_MIN_MINUTES = 15;     // 이만큼 넘게 있으면 "머묾" (신호·버스 대기는 보통 10분 안)
    private static final int PLAN_RADIUS_M = 150;       // 계획 장소와 이 안이면 계획 장소에 있던 것 (④ 도착 반경과 같음)
    private static final int MINUTES_PER_DAY = 24 * 60; // 하루 = 1440분. 떠난 시각이 이걸 넘으면 자정을 넘겨 머문 것(숙소)
    private static final int NAME_RADIUS_M = 80;        // 머문 자리에서 이 안의 가장 가까운 장소 이름을 붙임 (없으면 이름 없이)
    private static final int LODGING_NAME_RADIUS_M = 150; // 숙소로 보이는 곳은 숙박(STAY)만, 조금 더 넓게 찾음 (건물이 커서)
    private static final int MERGE_GAP_MINUTES = 30;    // 같은 이름 조각 사이가 이 안이면 한 곳으로 합침 (넓은 곳 안을 걸어 다닌 것)

    private final TripService tripService;
    private final TrackService trackService;
    private final PlaceService placeService; // ② 장소: 머문 곳 근처 장소 이름 찾기 (getNearbyPlaceList, 11/07)

    /** 회고: 계획 vs 실제 + GPS 선 */

    @Override
    public RetroResponse getRetro(long memberId, long tripId) {
        Trip trip = tripService.getTrip(memberId, tripId);
        RetroResponse response = new RetroResponse();
        response.setId(trip.getId());
        response.setTitle(trip.getTitle());
        response.setStartDate(trip.getStartDate());
        response.setEndDate(trip.getEndDate());
        response.setPeople(trip.getPeople()); // 정산 1인당 금액에 씀
        int visited = 0; // 실제로 다녀온 장소 수
        for (Stop stop : trip.getStops()) {

            // 도착 기록이 있으면 다녀온 곳으로 셈
            if (stop.getArrivedAt() != null) {
                visited++;
            }
            response.getStops().add(toRetroStop(stop));
           
        }
        response.setVisited(visited); // 
        List<TrackPoint> track = trackService.getTrackList(tripId); // gps 점 기록 
        response.setTrack(track);
        response.setMovedMeters(sumMovedMeters(track));
        List<ExtraStop> extras = findExtraStops(track, response.getStops()); // 계획 없이 머문 곳 (GPS로 찾음). 실제 도착·머문 분이 계산된 회고 칸을 넘김
        fillNames(extras);                                // 근처 장소 이름 붙이기 (② PlaceService)
        response.setExtraStops(mergeSameName(extras));    // 붙어 있는 같은 이름 조각은 하나로 (넓은 곳)
        return response;
    }

    // GPS 점 사이 거리를 기록 순서대로 더함(m). 탈것으로 이동한 거리도 포함
    // TODO: 4번 진짜 GPS가 들어오면 기록 간격을 보고, 걷기보다 빠른 구간은 빼서 "걸은 거리"로 바꿀지 정하기
    private static int sumMovedMeters(List<TrackPoint> track) {
        double total = 0;
        for (int i = 1; i< track.size(); i++) { 
            TrackPoint before = track.get(i - 1);
            TrackPoint now = track.get(i);
            total += Geo.distanceMeters(before.getLat(), before.getLng(), now.getLat(), now.getLng());
        }
           return (int) Math.round(total);
    }

    // GPS 점 시각을 "여행 첫날 0시부터 몇 분"으로. 예: 2일차 09:30 → 1440 + 570 = 2010
    private static int toTripMinutes(TrackPoint point) {
        return toTripMinutes(point.getDay(), point.getTime());
    }

    // 몇째 날 + HH:mm → "여행 첫날 0시부터 몇 분" (머문 곳 합치기에서도 씀)
    private static int toTripMinutes(int day, String time) {
        return (day - 1) * MINUTES_PER_DAY + Geo.toMinutes(time);
    }

    // GPS 점을 앞에서부터 "한 자리" 덩어리로 묶어, 오래 머문 곳 중 계획에 없던 곳만 담음
    private static List<ExtraStop> findExtraStops(List<TrackPoint> track, List<RetroStop> stops) {
        List<ExtraStop> result = new ArrayList<>(); // 찾은 머문 곳을 담을 빈 목록 
        int start = 0; // 지금 덩어리(한 자리)의 첫 점 번호
        while(start < track.size()) { // 첫점에 목록에서 아직 안 본 점이 남아 있는 동안 반복
            TrackPoint first = track.get(start); // 이 덩어리의 첫 GPS 점. 예: 카페 11:15 점
            int next = start + 1; // 덩어리를 벗어난 첫 점 번호 (처음엔 바로 다음 점부터 봄)
            while(next< track.size() && isNear(first, track.get(next))){ // 남은 점이 있고, 그리고 첫 점과 100m 안이면 → 다음 점으로
                next++;
            }
            if(next < track.size()) { // 벗어난 점이 있어야 머문 시간을 잼 (마지막 덩어리는 끝을 몰라서 뺌)
                int stay = toTripMinutes(track.get(next)) - toTripMinutes(first);
                ExtraStop extra = toExtraStop(first, stay, stops);
                if (extra != null) result.add(extra);
            }
            start = next;
        }
        return result;
    }

    // 두 GPS 점이 "한 자리"(100m 안)인가
    private static boolean isNear(TrackPoint a, TrackPoint b) {
        return Geo.distanceMeters(a.getLat(), a.getLng(), b.getLat(), b.getLng()) < STAY_RADIUS_M;
    }

    // 머문 덩어리 하나를 기준 (15분 , 150m)으로 걸러 ExtraStop으로 만듦, 머문 곳이 아니면 null
    private static ExtraStop toExtraStop(TrackPoint first, int stay, List<RetroStop> stops) {
        if(stay < STAY_MIN_MINUTES) return null; // 15분이 안 되면 머문 게 아님
        if (isDuringPlannedVisit(first, stops)) return null; // 그 시각에 계획 장소에 있던 기록이 있음 (넓은 곳 안쪽)
        if(isNearPlannedStop(first, stops)) return null; // 계획장소에 있던것

        String kind = "visit";
        int leaveMinutes = Geo.toMinutes(first.getTime()) + stay; // 떠난 시각(분). 1440 넘으면 다음 날
        if (leaveMinutes > MINUTES_PER_DAY) kind = "lodging";   // ② 자정을 넘겨 머물렀으면 숙소로 보임
        
        ExtraStop extra = new ExtraStop();
        extra.setDay(first.getDay());
        extra.setLat(first.getLat());
        extra.setLng(first.getLng());
        extra.setArrive(first.getTime());
        extra.setStay(stay);
        extra.setKind(kind);
        return extra;
    }
    // 이 GPS 점이 같은 날 계획 장소 근처(150m 안)인가
    private static boolean isNearPlannedStop(TrackPoint point, List<RetroStop> stops) {
        for (RetroStop stop : stops) {
            if (stop.getDay() != point.getDay()) continue; // 다른 날 장소는 안 봄
            if (stop.getLat() == null) continue;            // 좌표 없는 칸(직접 입력)은 못 잼
            double meters = Geo.distanceMeters(point.getLat(), point.getLng(), stop.getLat(), stop.getLng());
            if (meters < PLAN_RADIUS_M) return true;   
        }
        return false; // 다 봤는데 가까운 계획 장소가 없음
    }
    
    //이 GPS 점의 시각에 같은 날 계획 장소에 있던 기록 (실제 도착 - 떠남)이 있나 넓은 곳(궁궐 , 안쪽 깊은곳 건 물 자체가 큰 곳을 뺴려고)
 
    private static boolean isDuringPlannedVisit(TrackPoint point, List<RetroStop> stops) {
        int minutes = Geo.toMinutes(point.getTime()); // 예: 11:00 → 660
        for (RetroStop stop : stops) {
            if (stop.getDay() != point.getDay()) continue;                               // 다른 날은 안 봄
            if (stop.getActualArrive() == null || stop.getActualStay() == null) continue; // 도착·떠남 기록이 다 있어야 잼
            int arrive = Geo.toMinutes(stop.getActualArrive()); // 예: 창덕궁 10:05 → 605
            int leave = arrive + stop.getActualStay();          // 예: 605 + 95 = 700 (11:40)
            if (arrive <= minutes && minutes < leave) return true; // 도착 ≤ 그 시각 < 떠남
        }
        return false; // 그 시각에 있던 계획 장소가 없음
    }

    // 머문 곳마다 근처 장소 이름을 붙임. 숙소로 보이는 곳은 숙박(STAY)만 찾음. 못 찾으면 이름 없이 둠
    private void fillNames(List<ExtraStop> extras) {
        for (ExtraStop extra : extras) {
            List<Place> places; // 반경 안 장소, 가까운 순
            if ("lodging".equals(extra.getKind())) {
                places = placeService.getNearbyPlaceList(extra.getLat(), extra.getLng(), LODGING_NAME_RADIUS_M, "STAY");
            } else {
                places = placeService.getNearbyPlaceList(extra.getLat(), extra.getLng(), NAME_RADIUS_M, null);
            }
            if (!places.isEmpty()) extra.setName(places.get(0).getName()); // 하나라도 찾았으면 가장 가까운 곳 이름
        }
    }

    // 붙어 있는 머문 곳이 같은 이름이면 하나로 합침 (넓은 곳에 계획 없이 가서 여러 조각으로 잡힌 것)
    // 예: 창덕궁 10:12 (23분) + 창덕궁 10:44 (36분) → 창덕궁 10:12 (68분)
    private static List<ExtraStop> mergeSameName(List<ExtraStop> extras) {
        List<ExtraStop> result = new ArrayList<>();
        for (ExtraStop extra : extras) {
            ExtraStop last = null; // 바로 앞에 담은 머문 곳
            if (!result.isEmpty()) last = result.get(result.size() - 1);
            if (isSamePlace(last, extra)) {
                int lastArrive = toTripMinutes(last.getDay(), last.getArrive());
                int extraLeave = toTripMinutes(extra.getDay(), extra.getArrive()) + extra.getStay();
                last.setStay(extraLeave - lastArrive); // 앞 조각 도착 ~ 뒤 조각 떠남
            } else {
                result.add(extra);
            }
        }
        return result;
    }

    // 두 머문 곳이 "같은 곳의 조각"인가: 이름·종류가 같고, 앞 조각을 떠나고 30분 안에 뒤 조각이 시작
    private static boolean isSamePlace(ExtraStop last, ExtraStop extra) {
        if (last == null || last.getName() == null) return false; // 이름을 모르면 합칠 수 없음
        if (!last.getName().equals(extra.getName())) return false;
        if (!last.getKind().equals(extra.getKind())) return false;
        int lastLeave = toTripMinutes(last.getDay(), last.getArrive()) + last.getStay();
        int extraArrive = toTripMinutes(extra.getDay(), extra.getArrive());
        return extraArrive - lastLeave <= MERGE_GAP_MINUTES;
    }

    private static RetroStop toRetroStop(Stop stop) {
        RetroStop retro = new RetroStop();
        retro.setId(stop.getId());
        retro.setDay(stop.getDay());
        retro.setName(stop.getName());
        retro.setCategory(stop.getCategory());
        retro.setLat(stop.getLat());
        retro.setLng(stop.getLng());
        retro.setTime(stop.getTime());
        retro.setStay(stop.getStay());
        retro.setState(stop.getState());

        // 도착 기록이 있을 때만 실제 도착 시각을 채움. 없으면 null로 둠
        if (stop.getArrivedAt() != null) {
            LocalDateTime arrived = LocalDateTime.parse(stop.getArrivedAt());

            // 시각만 꺼내 앞 5글자만 잘라 적는 코드입니다.
            retro.setActualArrive(arrived.toLocalTime().toString().substring(0, 5));

            // 출발 기록까지 있어야 머문 시간을 계산할 수 있음
            if (stop.getDepartedAt() != null) {

                LocalDateTime departed = LocalDateTime.parse(stop.getDepartedAt());
                // ChronoUnit.MINUTES.between: 두 시각 사이가 몇 분인지 계산 (long으로 돌려줘서 int로 바꿈)
                // 출발 도착 시간을 계산해서 그렇게 함.

                retro.setActualStay((int) ChronoUnit.MINUTES.between(arrived, departed));
            }

        }

        return retro;
    }
}
