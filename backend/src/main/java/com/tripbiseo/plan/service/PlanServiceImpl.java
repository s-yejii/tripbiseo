package com.tripbiseo.plan.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.AiConditions;
import com.tripbiseo.plan.dto.AiDraft;
import com.tripbiseo.plan.dto.AiDraftStop;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.plan.dto.PlanStay;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ AI 일정: 대화 → 여행 조건, 조건 → 일정 초안 (저장은 안 함).
 * 초안 = RAG: ① 후보를 DB에서 찾고(PlanCandidateFinder) ② Gemini가 후보 id 중에서 고르고 순서를 정하고
 *       ③ 서버가 id를 다시 확인(후보 밖이면 이름으로 실제 장소 찾기, 못 찾으면 notes에 적고 뺌).
 * Gemini가 실패하면 규칙(PlanRuleMaker: 고른 종류·평점·거리)으로 초안 (오류 내지 않음). 좌표·회원 정보는 프롬프트에 넣지 않음.
 * 11/03: 여행 정보 화면의 지역 좌표·장소 종류·분위기 태그·출발지·숙소를 조건으로 받아 반영.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlanService.java (chat/generate/fallback)
 */
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private static final List<String> DEFAULT_TRANSPORTS = List.of("subway", "bus", "walk");
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_PROMPT_CANDIDATES = 90;
    private static final int TODAY_START_AFTER_MIN = 30;  // 오늘 출발이면 지금부터 30분 뒤부터
    private static final int FALLBACK_START_MIN = 600;    // AI 없이: 10:00부터 2시간 간격
    private static final int FALLBACK_GAP_MIN = 120;
    private static final int MIN_STAY = 10;
    private static final int MAX_STAY = 600;

    private final AiService aiService;
    private final PlaceService placeService;
    private final PlanCandidateFinder planCandidateFinder; // 후보 장소 찾기 (RAG의 찾기)
    private final PlanRuleMaker planRuleMaker;             // AI가 안 될 때 규칙으로 일정 + 숙소 칸 (11/03)

    /** 사용자 말 → 여행 조건. 보낸 조건은 남기고 Gemini가 새로 뽑은 값만 덮어씀. Gemini 실패는 오류 그대로 (API.md) */
    @Override
    public PlanChatResponse chatPlan(PlanChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw fail(400, "메시지를 1~300자로 입력해 주세요.");
        }
        PlanConditions conditions = request.getConditions() == null ? new PlanConditions() : request.getConditions();
        AiConditions answer = aiService.askJson(makeChatPrompt(conditions, message), AiConditions.class);
        mergeConditions(conditions, answer);
        fillDefaults(conditions);

        PlanChatResponse response = new PlanChatResponse();
        boolean hasReply = answer != null && answer.getReply() != null && !answer.getReply().isBlank();
        response.setReply(hasReply ? answer.getReply() : "조건을 정리했어요. 확인해 주세요.");
        response.setConditions(conditions);
        return response;
    }

    /** 조건 → 일정 초안: 후보 찾기 → Gemini 고르기(실패하면 꼭 갈 곳만) → 실제 장소로 바꾸기 */
    @Override
    public PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request) {
        PlanConditions conditions = request.getConditions();
        if (conditions == null) {
            throw fail(400, "여행 조건이 없어요.");
        }
        PlanGenerateResponse response = makeEmptyDraft(conditions);
        int days = (int) ChronoUnit.DAYS.between(LocalDate.parse(response.getStartDate()), LocalDate.parse(response.getEndDate())) + 1;
        String region = conditions.getRegion() == null ? "" : conditions.getRegion();
        boolean isNearMe = conditions.getAreaLat() == null && planCandidateFinder.isNearMe(region); // 지역을 골랐으면 내 위치는 안 씀
        Double baseLat = isNearMe ? request.getLat() : null; // 다른 지역을 말했으면 내 위치는 쓰지 않음
        Double baseLng = isNearMe ? request.getLng() : null;

        Map<String, Place> found = findCandidates(conditions, region, isNearMe, baseLat, baseLng, response.getNotes());
        Map<String, Double> scores = planCandidateFinder.scoreOf(found, conditions.getTags() == null ? List.of() : conditions.getTags());
        Map<String, Place> candidates = planCandidateFinder.sortByScore(found, scores); // 고른 태그가 많은 곳부터
        List<AiDraftStop> picked = pickStops(conditions, request.getMessages(), candidates, scores, days, response);
        addPickedStops(response, picked, candidates, days, baseLat, baseLng);
        planRuleMaker.addStayStops(response, conditions, days);
        if (response.getStops().isEmpty()) {
            boolean isMissingLocation = isNearMe && request.getLat() == null;
            throw fail(422, isMissingLocation ? "내 위치를 받지 못했어요. 브라우저 위치 권한을 허용하거나 지역 이름을 말해 주세요."
                                              : "일정에 넣을 장소를 찾지 못했어요. 가고 싶은 곳을 조금 더 자세히 말해 주세요.");
        }
        // day → time 순 (sort에 넘기는 규칙: day가 같으면 time 글자 순, "09:00" < "10:00")
        response.getStops().sort((first, second) -> first.getDay().equals(second.getDay())
                ? first.getTime().compareTo(second.getTime()) : first.getDay() - second.getDay());
        return response;
    }

    // ---------------------------------------------------------------- 대화 → 조건

    /** 이전 조건과 오늘 날짜를 주고 JSON으로만 답하게 함 (좌표 없음) */
    private String makeChatPrompt(PlanConditions conditions, String message) {
        LocalDate today = Geo.now().toLocalDate();
        String dayName = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        return "너는 여행 일정 비서야. 오늘은 " + today + "(" + dayName + ")이야.\n"
             + "사용자 말에서 여행 조건을 뽑아 JSON만 출력해. 말하지 않은 값은 null로 둬(이전 조건을 유지할게).\n"
             + "이동 수단(transports)은 사용자가 직접 말했을 때만 채우고 짐작하지 마. \"내 위치·여기·이 근처\"라고 하면 region은 \"내 위치\".\n"
             + "이전 조건:\n" + toConditionText(conditions)
             + "사용자: \"" + message + "\"\n"
             + "{\"reply\": \"조건을 정리했다는 한두 문장 + 확인 부탁\", \"title\": \"여행 이름(15자 이내)\", \"region\": \"지역(예: 서울 종로)\", "
             + "\"startDate\": \"YYYY-MM-DD\", \"endDate\": \"YYYY-MM-DD\", \"people\": 정수, \"transports\": [\"bus\"|\"subway\"|\"walk\"|\"taxi\"|\"car\"], "
             + "\"mustVisit\": [\"꼭 갈 장소\"], \"fixed\": [{\"name\": \"시간이 정해진 일정 장소\", \"time\": \"HH:mm\"}], \"style\": \"여행 스타일 한 줄\"}";
    }

    /** 조건 → 프롬프트용 글 */
    private String toConditionText(PlanConditions conditions) {
        StringBuilder text = new StringBuilder(); // StringBuilder: 글자를 여러 번 이어 붙일 때
        appendLine(text, "title", conditions.getTitle());
        appendLine(text, "region", conditions.getRegion());
        appendLine(text, "startDate", conditions.getStartDate());
        appendLine(text, "endDate", conditions.getEndDate());
        appendLine(text, "people", conditions.getPeople() == null ? null : String.valueOf(conditions.getPeople()));
        appendLine(text, "transports", conditions.getTransports() == null ? null : String.join(",", conditions.getTransports()));
        appendLine(text, "mustVisit", conditions.getMustVisit() == null ? null : String.join(",", conditions.getMustVisit()));
        if (conditions.getFixed() != null) {
            for (FixedVisit fixed : conditions.getFixed()) {
                appendLine(text, "fixed", fixed.getName() + " " + fixed.getTime());
            }
        }
        appendLine(text, "style", conditions.getStyle());
        appendLine(text, "kinds(이 종류 위주로)", conditions.getKinds() == null ? null : String.join(",", conditions.getKinds()));
        appendLine(text, "tags(이런 곳 우선, 후보는 이미 이 순서)", conditions.getTags() == null ? null : String.join(",", conditions.getTags()));
        appendLine(text, "origin(1일차 출발지)", conditions.getOriginName());
        if (conditions.getStays() != null) {
            for (PlanStay stay : conditions.getStays()) {
                appendLine(text, "stay(이날 밤 숙소, 다음 날 여기서 출발. 일정에는 넣지 마 — 서버가 넣음)", stay.getName() + " " + stay.getCheckInDate());
            }
        }
        return text.length() == 0 ? "(없음)\n" : text.toString();
    }

    private void appendLine(StringBuilder text, String key, String value) {
        if (value != null && !value.isBlank()) {
            text.append("- ").append(key).append(": ").append(value).append("\n");
        }
    }

    /** Gemini가 새로 뽑은 값만 덮어씀 (빈 목록은 이전 값을 지우지 않음) */
    private void mergeConditions(PlanConditions conditions, AiConditions answer) {
        if (answer == null) {
            return;
        }
        if (hasText(answer.getTitle())) {
            conditions.setTitle(answer.getTitle());
        }
        if (hasText(answer.getRegion())) {
            conditions.setRegion(answer.getRegion());
        }
        if (hasText(answer.getStartDate())) {
            conditions.setStartDate(answer.getStartDate());
        }
        if (hasText(answer.getEndDate())) {
            conditions.setEndDate(answer.getEndDate());
        }
        if (answer.getPeople() != null) {
            conditions.setPeople(answer.getPeople());
        }
        mergeLists(conditions, answer);
        if (hasText(answer.getStyle())) {
            conditions.setStyle(answer.getStyle());
        }
    }

    private void mergeLists(PlanConditions conditions, AiConditions answer) {
        if (answer.getTransports() != null && !answer.getTransports().isEmpty()) {
            conditions.setTransports(answer.getTransports());
        }
        if (answer.getMustVisit() != null && !answer.getMustVisit().isEmpty()) {
            conditions.setMustVisit(answer.getMustVisit());
        }
        if (answer.getFixed() != null && !answer.getFixed().isEmpty()) {
            conditions.setFixed(answer.getFixed());
        }
    }

    /** 기본값: 시작일 오늘, 끝날 = 시작일, 인원 1, 수단 지하철·버스·도보 */
    private void fillDefaults(PlanConditions conditions) {
        if (!hasText(conditions.getStartDate())) {
            conditions.setStartDate(Geo.now().toLocalDate().toString());
        }
        if (!hasText(conditions.getEndDate())) {
            conditions.setEndDate(conditions.getStartDate());
        }
        if (conditions.getPeople() == null) {
            conditions.setPeople(1);
        }
        if (conditions.getTransports() == null || conditions.getTransports().isEmpty()) {
            conditions.setTransports(DEFAULT_TRANSPORTS);
        }
    }

    // ---------------------------------------------------------------- 조건 → 초안

    /** 조건 값으로 빈 초안 (transports는 조건에 없으면 [] — API.md) */
    private PlanGenerateResponse makeEmptyDraft(PlanConditions conditions) {
        PlanGenerateResponse response = new PlanGenerateResponse();
        String today = Geo.now().toLocalDate().toString();
        response.setTitle(hasText(conditions.getTitle()) ? cut(conditions.getTitle(), MAX_TITLE_LENGTH) : "새 여행");
        response.setStartDate(hasText(conditions.getStartDate()) ? conditions.getStartDate() : today);
        response.setEndDate(hasText(conditions.getEndDate()) ? conditions.getEndDate() : response.getStartDate());
        response.setPeople(conditions.getPeople() == null ? 1 : conditions.getPeople());
        response.setTransports(conditions.getTransports() == null ? new ArrayList<>() : conditions.getTransports());
        return response;
    }

    /** R 찾기: 내 위치 근처 + 꼭 갈 곳과 주변 + 지역. 꼭 갈 곳을 못 찾으면 notes에 */
    private Map<String, Place> findCandidates(PlanConditions conditions, String region, boolean isNearMe,
                                              Double baseLat, Double baseLng, List<PlanNote> notes) {
        Map<String, Place> candidates = planCandidateFinder.newCandidates();
        List<String> kinds = planRuleMaker.kindsOf(conditions);
        if (conditions.getAreaLat() != null) {
            planCandidateFinder.addKinds(candidates, conditions.getAreaLat(), conditions.getAreaLng(), conditions.getAreaType(), kinds);
        } else if (baseLat != null && baseLng != null) {
            planCandidateFinder.addKinds(candidates, baseLat, baseLng, "dong", kinds);
            planCandidateFinder.addNearMe(candidates, baseLat, baseLng);
        }
        if (conditions.getMustVisit() != null) {
            for (String name : conditions.getMustVisit()) {
                if (planCandidateFinder.addMustVisit(candidates, name, baseLat, baseLng) == null) {
                    notes.add(makeNote(name + "의 위치를 찾지 못했어요", placeService.getNaverUrl(name, null)));
                }
            }
        }
        if (!isNearMe && conditions.getAreaLat() == null) {
            planCandidateFinder.addArea(candidates, region); // 지역 좌표 없이 이름만 말했을 때 (대화)
        }
        return candidates;
    }

    /** G 고르기: Gemini가 후보 안에서 고름. 키가 없거나 실패하면 규칙으로 (notes에 안내) */
    private List<AiDraftStop> pickStops(PlanConditions conditions, List<String> messages, Map<String, Place> candidates,
                                        Map<String, Double> scores, int days, PlanGenerateResponse response) {
        if (aiService.isReady()) {
            try {
                AiDraft draft = aiService.askJson(makeDraftPrompt(conditions, messages, candidates, days, response.getStartDate()), AiDraft.class);
                if (draft != null && hasText(draft.getTitle())) {
                    response.setTitle(cut(draft.getTitle(), MAX_TITLE_LENGTH));
                }
                if (draft != null && !draft.getStops().isEmpty()) {
                    return draft.getStops();
                }
            } catch (ApiException e) {
                response.getNotes().add(makeNote("AI 연결이 안 돼서 고른 종류·평점·거리로 일정을 짰어요 (" + e.getMessage() + ")", null));
                return planRuleMaker.makeStops(conditions, candidates, scores, days, response.getStartDate(), makeFallbackStops(conditions));
            }
        }
        response.getNotes().add(makeNote("AI 연결이 안 돼서 고른 종류·평점·거리로 일정을 짰어요", null));
        return planRuleMaker.makeStops(conditions, candidates, scores, days, response.getStartDate(), makeFallbackStops(conditions));
    }

    /** 후보 목록(id | 이름 | 분류 | 평점 | 주소)과 규칙을 주고 JSON으로만 답하게 함. 좌표는 넣지 않음 */
    private String makeDraftPrompt(PlanConditions conditions, List<String> messages, Map<String, Place> candidates, int days, String startDate) {
        StringBuilder list = new StringBuilder();
        int count = 0;
        for (Place place : candidates.values()) {
            if (count++ >= MAX_PROMPT_CANDIDATES) {
                break;
            }
            list.append(place.getId()).append(" | ").append(place.getName()).append(" | ").append(place.getCategory());
            if (place.getRating() != null) {
                list.append(" | ★").append(place.getRating()).append("(").append(place.getReviewCount()).append(")");
            }
            list.append(" | ").append(place.getAddress()).append("\n");
        }
        String todayRule = "";
        if (startDate.equals(Geo.now().toLocalDate().toString())) {
            todayRule = "오늘 출발하는 여행이야. 1일차 일정은 " + Geo.toHhmm(Geo.nowMinutes() + TODAY_START_AFTER_MIN) + " 이후로만 짜.\n";
        }
        return "여행 일정을 짜 줘. 시간 계산은 대략이면 돼(서버가 실제 이동 시간으로 다시 검사해).\n"
             + "조건:\n" + toConditionText(conditions)
             + "사용자가 한 말: " + (messages == null ? "" : String.join(" / ", messages)) + "\n"
             + "규칙: 아래 후보 목록에서 고르고 id를 그대로 적어. 꼭 갈 곳과 시간 고정 일정은 반드시 넣고, 고정 일정은 그 시각으로.\n"
             + "꼭 갈 곳이 '분식집·카페'처럼 종류면 후보 중 그 종류에서 골라. 목록에 없는 장소가 꼭 필요하면 id는 null, name은 실제 상호명으로.\n"
             + "하루 4~6곳, 여러 날이면 날마다 나눠서, 점심(12시 전후)·저녁 시간엔 식당, 가까운 곳끼리 이어서.\n"
             + "기간: " + days + "일 (day는 1부터)\n" + todayRule
             + "후보(id | 이름 | 분류 | 평점 | 주소):\n" + list
             + "JSON만: {\"title\": \"여행 이름\", \"stops\": [{\"day\": 1, \"id\": \"후보 id 또는 null\", \"name\": \"장소 이름\", \"time\": \"HH:mm\", \"stay\": 머무는 분, \"fixed\": false}]}";
    }

    /** AI 없이: 꼭 갈 곳을 10시부터 2시간 간격, 고정 일정은 그 시각 */
    private List<AiDraftStop> makeFallbackStops(PlanConditions conditions) {
        List<AiDraftStop> stops = new ArrayList<>();
        int clock = FALLBACK_START_MIN;
        if (conditions.getMustVisit() != null) {
            for (String name : conditions.getMustVisit()) {
                stops.add(makeDraftStop(name, Geo.toHhmm(clock), 90, false));
                clock += FALLBACK_GAP_MIN;
            }
        }
        if (conditions.getFixed() != null) {
            for (FixedVisit fixed : conditions.getFixed()) {
                stops.add(makeDraftStop(fixed.getName(), fixed.getTime(), 120, true));
            }
        }
        return stops;
    }

    private AiDraftStop makeDraftStop(String name, String time, int stay, boolean isFixed) {
        AiDraftStop stop = new AiDraftStop();
        stop.setDay(1);
        stop.setName(name);
        stop.setTime(time);
        stop.setStay(stay);
        stop.setFixed(isFixed);
        return stop;
    }

    /** 검사 + 실제 장소로: 후보 id면 그 장소, 아니면 이름으로 찾기, 못 찾으면 notes에 적고 뺌 */
    private void addPickedStops(PlanGenerateResponse response, List<AiDraftStop> picked, Map<String, Place> candidates,
                                int days, Double baseLat, Double baseLng) {
        Place first = candidates.isEmpty() ? null : candidates.values().iterator().next();
        Double nearLat = baseLat != null ? baseLat : first == null ? null : first.getLat();
        Double nearLng = baseLng != null ? baseLng : first == null ? null : first.getLng();
        for (AiDraftStop pick : picked) {
            Place place = pick.getId() == null ? null : candidates.get(pick.getId()); // 후보 밖 id는 믿지 않음
            if (place == null) {
                place = placeService.getPlaceByName(pick.getName(), nearLat, nearLng);
            }
            if (place == null) {
                String name = pick.getName() == null ? "" : pick.getName();
                response.getNotes().add(makeNote(name + "의 위치를 찾지 못해 뺐어요", placeService.getNaverUrl(name, null)));
                continue;
            }
            response.getStops().add(toDraftStop(pick, place, days));
        }
    }

    /** 초안 칸 (②의 저장 규칙: day 1~기간, time HH:mm, stay 10~600) */
    private Stop toDraftStop(AiDraftStop pick, Place place, int days) {
        Stop stop = new Stop();
        int day = pick.getDay() == null ? 1 : pick.getDay();
        stop.setDay(Math.min(days, Math.max(1, day)));
        stop.setPlaceId(place.getId());
        stop.setName(place.getName());
        stop.setCategory(place.getCategory());
        stop.setAddress(place.getAddress());
        stop.setLat(place.getLat());
        stop.setLng(place.getLng());
        boolean isTime = pick.getTime() != null && pick.getTime().matches("([01]\\d|2[0-3]):[0-5]\\d");
        stop.setTime(isTime ? pick.getTime() : "10:00");
        stop.setStay(pick.getStay() == null ? 60 : Math.max(MIN_STAY, Math.min(MAX_STAY, pick.getStay())));
        stop.setFixed(Boolean.TRUE.equals(pick.getFixed()));
        stop.setState("todo");
        return stop;
    }

    private PlanNote makeNote(String text, String url) {
        PlanNote note = new PlanNote();
        note.setText(text);
        note.setUrl(url);
        return note;
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private String cut(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
