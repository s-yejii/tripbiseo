package com.tripbiseo.plan.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.member.service.MemberService;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ AI 일정. 지금은 가짜 값이에요.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlanService.java
 * 공통 주의: 프롬프트에 GPS 좌표·회원 정보를 넣지 않기 (무료 등급). Map 대신 DTO로 받기 — aiService.askJson(prompt, PlanConditions.class)
 */
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private final AiService aiService;          // TODO: ③ Gemini 호출
    private final PlaceService placeService;    // TODO: ③ 후보 장소: getAreaPlaceList(지역), getNearbyPlaceList(내 위치 2km), getPlaceByName
    private final MemberService memberService;  // TODO: ③ getStyle(memberId)로 여행 스타일 반영

    /** 사용자 말 → 여행 조건 */
    // TODO: ③ 조건 정리. message 1~300자 검사 → aiService.askJson(prompt, 조건 DTO) → 보낸 조건에 새 값만 덮어쓰기 → 기본값 채우기.
    //       참고: 예전 PlanService.chat. 주의: 예전 str/maps 같은 static 도우미 대신 DTO 필드로
    @Override
    public PlanChatResponse chatPlan(PlanChatRequest request) {
        PlanConditions conditions = request.getConditions() == null ? new PlanConditions() : request.getConditions();
        String today = Geo.now().toLocalDate().toString();
        conditions.setTitle("종로 하루");
        conditions.setRegion("서울 종로");
        conditions.setStartDate(today);
        conditions.setEndDate(today);
        conditions.setPeople(2);
        conditions.setTransports(List.of("subway", "bus", "walk"));
        conditions.setMustVisit(List.of("경복궁"));
        FixedVisit fixed = new FixedVisit();
        fixed.setName("세종문화회관");
        fixed.setTime("19:00");
        conditions.setFixed(List.of(fixed));
        conditions.setStyle("여유롭게");

        PlanChatResponse response = new PlanChatResponse();
        response.setReply("종로 하루 여행으로 정리했어요. 맞나요?");
        response.setConditions(conditions);
        return response;
    }

    /** 조건 → 일정 초안 */
    // TODO: ③ 일정 초안. 후보 장소 → Gemini가 고르고 순서 → 서버가 시각 계산 → Stop 초안(②의 저장 규칙: time HH:mm, stay 10~600).
    //       참고: 예전 PlanService.generate/fallback. 주의: Gemini 실패(ApiException)는 catch해서 notes에 적고 꼭 갈 곳만으로.
    //       region이 "내 위치"면 lat·lng 주변 2km 후보 (좌표는 Gemini에 보내지 않음). 위치도 없으면 422
    @Override
    public PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request) {
        PlanConditions conditions = request.getConditions() == null ? new PlanConditions() : request.getConditions();
        String today = Geo.now().toLocalDate().toString();
        PlanGenerateResponse response = new PlanGenerateResponse();
        response.setTitle(conditions.getTitle() == null ? "새 여행" : conditions.getTitle());
        response.setStartDate(conditions.getStartDate() == null ? today : conditions.getStartDate());
        response.setEndDate(conditions.getEndDate() == null ? response.getStartDate() : conditions.getEndDate());
        response.setPeople(conditions.getPeople() == null ? 1 : conditions.getPeople());
        response.setTransports(conditions.getTransports() == null ? new ArrayList<>() : conditions.getTransports());
        response.getStops().add(draftStop("e70caff5-27cf-5c76-a1f3-201b7032d672", "경복궁", "관광지", "서울 종로구 사직로 161",
                37.5796, 126.977, "10:00", 90, false));
        response.getStops().add(draftStop(null, "세종문화회관", "문화시설", "서울 종로구 세종대로 175",
                37.5725, 126.9759, "19:00", 120, true));
        return response;
    }

    // ---------------------------------------------------------------- 가짜 값 (진짜 로직을 만들면 지우기)
    private static Stop draftStop(String placeId, String name, String category, String address,
                                  double lat, double lng, String time, int stay, boolean isFixed) {
        Stop stop = new Stop();
        stop.setDay(1);
        stop.setPlaceId(placeId);
        stop.setName(name);
        stop.setCategory(category);
        stop.setAddress(address);
        stop.setLat(lat);
        stop.setLng(lng);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        stop.setState("todo");
        return stop;
    }
}
