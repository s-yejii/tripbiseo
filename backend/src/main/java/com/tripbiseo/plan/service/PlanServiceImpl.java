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
import com.tripbiseo.live.dto.PlaceTagCount;
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
 * 11/04: 칩으로 못 고르는 요청(needs: "4명이 앉을 자리")은 ReviewNeedChecker가 후보 리뷰로 확인해 점수·후보에 반영.
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/PlanService.java (chat/generate/fallback)
 */
@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    private static final List<String> DEFAULT_TRANSPORTS = List.of("subway", "bus", "walk");
    private static final List<String> KIND_CODES = List.of("food", "cafe", "bar", "tour", "culture", "shop"); // 대화로 더할 수 있는 종류
    private static final List<String> TAG_CODES = List.of("mood", "quiet", "value", "no_wait", "solo");       // 대화로 더할 수 있는 태그
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_PROMPT_CANDIDATES = 90;
    private static final int MAX_REASON_LENGTH = 40;      // Gemini 이유 한 줄
    private static final int MAX_MEMO_LENGTH = 200;       // trip_stop.memo 저장 규칙
    private static final int TODAY_START_AFTER_MIN = 30;  // 오늘 출발이면 지금부터 30분 뒤부터
    private static final int FALLBACK_START_MIN = 600;    // AI 없이: 10:00부터 2시간 간격
    private static final int FALLBACK_GAP_MIN = 120;
    private static final int FALLBACK_STEP_MIN = 30;      // 고정 일정과 겹치면 꼭 갈 곳을 30분씩 뒤로
    private static final int LAST_MUST_START_MIN = 1260;  // 꼭 갈 곳은 늦어도 21:00 시작
    private static final int MUST_STAY_MIN = 90;
    private static final int FIXED_STAY_MIN = 120;
    private static final int MIN_STAY = 10;
    private static final int MAX_STAY = 600;
    private static final int MAX_NEEDS = 3;          // 칩에 없는 요청은 3개까지 (리뷰 확인이 요청마다 Gemini를 불러서)
    private static final int MAX_NEED_LENGTH = 20;

    private final AiService aiService;
    private final PlaceService placeService;
    private final PlanCandidateFinder planCandidateFinder; // 후보 장소 찾기 (RAG의 찾기)
    private final PlanRuleMaker planRuleMaker;             // AI가 안 될 때 규칙으로 일정 + 숙소 칸 (11/03)
    private final ReviewNeedChecker reviewNeedChecker;     // 칩에 없는 요청을 후보 리뷰로 확인 (11/04)

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
        Map<String, PlaceTagCount> tagCounts = planCandidateFinder.countTags(found);
        Map<String, Double> scores = planCandidateFinder.scoreOf(found, tagCounts, conditions.getTags());
        Map<String, List<String>> reasons = planCandidateFinder.reasonsOf(found, tagCounts, conditions.getTags());
        reviewNeedChecker.check(conditions, found, scores, reasons, response.getNotes()); // 안 맞는 곳은 빼고, 맞는 곳은 점수·리뷰 근거 (11/04)
        Map<String, Place> candidates = planCandidateFinder.sortByScore(found, scores); // 고른 태그가 많은 곳부터
        List<AiDraftStop> picks = pickStops(conditions, request.getMessages(), candidates, reasons, days, response);
        List<String> warnings = new ArrayList<>();
        List<AiDraftStop> filled = planRuleMaker.fillSlots(conditions, candidates, scores, days, response.getStartDate(), picks, warnings);
        for (String warning : warnings) {
            response.getNotes().add(makeNote(warning, null));
        }
        addPickedStops(response, filled, candidates, reasons, days, baseLat, baseLng);
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
             + "\"mustVisit\": [\"이번 말에서 새로 나온 꼭 갈 장소\"], \"fixed\": [{\"name\": \"이번 말에서 새로 나온 시간이 정해진 일정\", \"time\": \"HH:mm\"}], "
             + "\"kinds\": [\"새로 원한 장소 종류 코드\"], \"tags\": [\"새로 원한 분위기 코드\"], \"needs\": [\"새로 말한 장소 요청\"], \"style\": \"여행 스타일 한 줄\"}\n"
             + "mustVisit·fixed·kinds·tags·needs는 이번 말에서 새로 나온 것만 적어(이전 것은 서버가 남겨 둬). 상호명은 사용자가 말한 그대로.\n"
             + "kinds 코드: food 밥집, cafe 카페·디저트, bar 술집, tour 관광·산책, culture 전시·문화, shop 쇼핑. 그 종류에 가고 싶다고 했을 때만.\n"
             + "tags 코드: mood 분위기 좋은, quiet 조용한, value 가성비, no_wait 웨이팅 없는(웨이팅 싫다 포함), solo 혼밥. 그런 곳을 원한다고 했을 때만.\n"
             + "needs: kinds·tags 코드로 못 담는 장소 조건을 15자 이내 짧은 말로 (예: 4명이 앉을 자리, 주차되는 곳, 아이랑 가기 좋은). 장소 이름·시각·이동 수단·걷는 시간은 넣지 마.";
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
        appendLine(text, "kinds(이 종류 위주로, 하루 칸에 이미 반영)", conditions.getKinds() == null ? null : toKindNames(conditions.getKinds()));
        appendLine(text, "tags(이런 곳 우선)", String.join(",", planCandidateFinder.toTagNames(conditions.getTags())));
        appendLine(text, "needs(장소 요청, 서버가 리뷰로 확인함)", conditions.getNeeds() == null ? null : String.join(",", conditions.getNeeds()));
        appendLine(text, "origin(1일차 출발지)", conditions.getOriginName());
        if (conditions.getStays() != null) {
            for (PlanStay stay : conditions.getStays()) {
                appendLine(text, "stay(이날 밤 숙소, 다음 날 여기서 출발. 일정에는 넣지 마 — 서버가 넣음)", stay.getName() + " " + stay.getCheckInDate());
            }
        }
        return text.length() == 0 ? "(없음)\n" : text.toString();
    }

    /** 종류 코드 목록 → "관광·산책,카페" */
    private String toKindNames(List<String> kinds) {
        List<String> names = new ArrayList<>();
        for (String kind : kinds) {
            names.add(planCandidateFinder.kindName(kind));
        }
        return String.join(",", names);
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
        if (hasText(answer.getRegion()) && !isSameRegion(conditions.getRegion(), answer.getRegion())) {
            conditions.setRegion(answer.getRegion());
            conditions.setAreaLat(null); // 말로 지역을 바꾸면 화면에서 고른 지역 좌표는 안 맞으니 지움 → 지역 이름으로 후보를 찾음
            conditions.setAreaLng(null);
            conditions.setAreaType(null);
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
        // 꼭 갈 곳·고정 일정·종류·태그는 새로 말한 것을 더함 (빼기는 화면의 조건 카드에서 사용자가 직접)
        conditions.setMustVisit(addNew(conditions.getMustVisit(), answer.getMustVisit(), null));
        conditions.setKinds(addNew(conditions.getKinds(), answer.getKinds(), KIND_CODES));
        conditions.setTags(addNew(conditions.getTags(), answer.getTags(), TAG_CODES));
        conditions.setNeeds(cutNeeds(addNew(conditions.getNeeds(), answer.getNeeds(), null)));
        if (answer.getFixed() != null) {
            List<FixedVisit> fixedList = conditions.getFixed() == null ? new ArrayList<>() : new ArrayList<>(conditions.getFixed());
            for (FixedVisit fixed : answer.getFixed()) {
                if (fixed != null && hasText(fixed.getName()) && !hasFixed(fixedList, fixed.getName())) {
                    fixedList.add(fixed);
                }
            }
            conditions.setFixed(fixedList);
        }
    }

    /** 칩에 없는 요청은 3개·20자까지 (넘는 건 버림) */
    private List<String> cutNeeds(List<String> needs) {
        if (needs == null) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (String need : needs) {
            if (result.size() < MAX_NEEDS) {
                result.add(cut(need, MAX_NEED_LENGTH));
            }
        }
        return result;
    }

    /** 같은 지역인지: Gemini가 "성수동"처럼 이전 지역을 줄여 다시 적어도 바뀐 걸로 보지 않음 (한쪽이 다른 쪽을 포함하면 같음) */
    private boolean isSameRegion(String current, String answered) {
        if (current == null) {
            return false;
        }
        String before = current.replace(" ", "");
        String after = answered.replace(" ", "");
        return before.contains(after) || after.contains(before);
    }

    /** 기존 목록 + 새 값 (중복 빼고, allowed가 있으면 그 안의 값만). 둘 다 비면 기존 그대로 */
    private List<String> addNew(List<String> current, List<String> added, List<String> allowed) {
        if (added == null || added.isEmpty()) {
            return current;
        }
        List<String> result = current == null ? new ArrayList<>() : new ArrayList<>(current);
        for (String value : added) {
            boolean isAllowed = allowed == null || allowed.contains(value);
            if (hasText(value) && isAllowed && !result.contains(value.trim())) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private boolean hasFixed(List<FixedVisit> fixedList, String name) {
        for (FixedVisit fixed : fixedList) {
            if (name.equals(fixed.getName())) {
                return true;
            }
        }
        return false;
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

    /**
     * G 고르기: Gemini가 하루 칸마다 후보 안에서 고르고 이유를 씀. 키가 없거나 실패하면 꼭 갈 곳·고정 일정만 돌려줌
     * (어느 쪽이든 칸 검사·빈 칸 채우기는 PlanRuleMaker.fillSlots가 함)
     */
    private List<AiDraftStop> pickStops(PlanConditions conditions, List<String> messages, Map<String, Place> candidates,
                                        Map<String, List<String>> reasons, int days, PlanGenerateResponse response) {
        if (aiService.isReady()) {
            try {
                AiDraft draft = aiService.askJson(makeDraftPrompt(conditions, messages, candidates, reasons, days, response.getStartDate()), AiDraft.class);
                if (draft != null && hasText(draft.getTitle())) {
                    response.setTitle(cut(draft.getTitle(), MAX_TITLE_LENGTH));
                }
                if (draft != null && !draft.getStops().isEmpty()) {
                    return addMissingMust(conditions, draft.getStops(), candidates);
                }
            } catch (ApiException e) {
                response.getNotes().add(makeNote("AI 연결이 안 돼서 고른 종류·평점·거리로 일정을 짰어요 (" + e.getMessage() + ")", null));
                return makeFallbackStops(conditions);
            }
        }
        response.getNotes().add(makeNote("AI 연결이 안 돼서 고른 종류·평점·거리로 일정을 짰어요", null));
        return makeFallbackStops(conditions);
    }

    /**
     * 프롬프트: 조건 + 하루 칸 틀(S1 10:00 관광…) + 후보 목록(id | 이름 | 종류 | 분류 | 평점 | 리뷰 근거 | 주소). 좌표는 넣지 않음.
     * Gemini는 칸마다 칸 종류에 맞는 후보 하나와 고른 이유를 JSON으로만 답함
     */
    private String makeDraftPrompt(PlanConditions conditions, List<String> messages, Map<String, Place> candidates,
                                   Map<String, List<String>> reasons, int days, String startDate) {
        String todayRule = "";
        if (startDate.equals(Geo.now().toLocalDate().toString())) {
            todayRule = "오늘 출발하는 여행이야. 1일차는 " + Geo.toHhmm(Geo.nowMinutes() + TODAY_START_AFTER_MIN) + " 이후 칸만 채워.\n";
        }
        return "여행 일정을 짜 줘.\n"
             + "조건:\n" + toConditionText(conditions)
             + "사용자가 한 말: " + (messages == null ? "" : String.join(" / ", messages)) + "\n"
             + "기간: " + days + "일 (day는 1부터). 날마다 아래 칸을 채워:\n" + makeSlotText(conditions) + todayRule
             + "규칙:\n"
             + "- 칸마다 후보 목록에서 그 칸 종류에 맞는 곳 하나를 골라 id를 그대로 적어. 같은 곳을 두 번 쓰지 마.\n"
             + "- tags(이런 곳 우선)가 있으면 리뷰 근거에 그 태그가 있는 곳을 먼저 골라.\n"
             + "- needs(장소 요청)가 있으면 리뷰 근거에 그 요청이 적힌 곳을 먼저 골라.\n"
             + "- 앞 칸 장소와 주소(구·동)가 가까운 곳으로 이어서 동선이 짧게.\n"
             + "- 꼭 갈 곳과 시간 고정 일정은 slot을 null로, time은 고정 시각(꼭 갈 곳은 알맞은 시각)으로 넣어. 그 시간과 겹치는 칸은 비워.\n"
             + "- reason은 왜 골랐는지 30자 이내. 후보 목록에 있는 정보(종류·평점·리뷰 근거·위치)만 근거로 써.\n"
             + "후보(id | 이름 | 종류 | 분류 | 평점(리뷰 수) | 리뷰 근거 | 주소):\n" + makeCandidateText(conditions, candidates, reasons)
             + "JSON만: {\"title\": \"여행 이름\", \"stops\": [{\"day\": 1, \"slot\": \"S1 또는 null\", \"id\": \"후보 id 또는 null\", "
             + "\"name\": \"장소 이름\", \"time\": \"HH:mm\", \"stay\": 머무는 분, \"fixed\": false, \"reason\": \"고른 이유\"}]}";
    }

    /** 하루 칸 틀 글: "S1 10:00 (90분) 관광·산책 또는 전시·문화" 한 줄씩 */
    private String makeSlotText(PlanConditions conditions) {
        StringBuilder text = new StringBuilder();
        List<String[]> slots = planRuleMaker.getSlotList(conditions);
        for (int i = 0; i < slots.size(); i++) {
            List<String> names = new ArrayList<>();
            for (String kind : slots.get(i)[0].split(",")) {
                names.add(planCandidateFinder.kindName(kind));
            }
            text.append("S").append(i + 1).append(" ").append(slots.get(i)[1]).append(" (").append(slots.get(i)[2]).append("분) ")
                .append(String.join(" 또는 ", names)).append("\n");
        }
        return text.toString();
    }

    /** 후보 목록 글: 고른 종류마다 골고루 (PlanCandidateFinder.pickForPrompt), 한 줄에 종류와 리뷰 근거까지 */
    private String makeCandidateText(PlanConditions conditions, Map<String, Place> candidates, Map<String, List<String>> reasons) {
        Map<String, Place> shown = planCandidateFinder.pickForPrompt(candidates, planRuleMaker.kindsOf(conditions),
                conditions.getMustVisit(), MAX_PROMPT_CANDIDATES);
        StringBuilder list = new StringBuilder();
        for (Place place : shown.values()) {
            list.append(place.getId()).append(" | ").append(place.getName())
                .append(" | ").append(planCandidateFinder.kindName(place.getKind()))
                .append(" | ").append(place.getCategory());
            list.append(" | ").append(place.getRating() == null ? "-" : "★" + place.getRating() + "(" + place.getReviewCount() + ")");
            List<String> found = reasons.get(place.getId());
            list.append(" | ").append(found == null ? "-" : String.join(", ", found));
            list.append(" | ").append(place.getAddress()).append("\n");
        }
        return list.toString();
    }

    /** Gemini가 빠뜨린 꼭 갈 곳·고정 일정을 더함 (사용자가 말한 건 반드시 일정에). 고정은 그 시각, 꼭 갈 곳은 칸 없이 */
    private List<AiDraftStop> addMissingMust(PlanConditions conditions, List<AiDraftStop> picks, Map<String, Place> candidates) {
        List<AiDraftStop> result = new ArrayList<>(picks);
        for (AiDraftStop must : makeFallbackStops(conditions)) {
            if (!hasPick(picks, must.getName(), candidates)) {
                result.add(must);
            }
        }
        return result;
    }

    /** Gemini 답에 그 이름의 장소가 있는지 (이름이 서로 포함되면 같은 곳) */
    private boolean hasPick(List<AiDraftStop> picks, String name, Map<String, Place> candidates) {
        for (AiDraftStop pick : picks) {
            Place place = pick.getId() == null ? null : candidates.get(pick.getId());
            String pickName = place != null ? place.getName() : pick.getName();
            if (pickName != null && (pickName.contains(name) || name.contains(pickName))) {
                return true;
            }
        }
        return false;
    }

    /** AI 없이: 꼭 갈 곳을 10시부터 2시간 간격, 고정 일정은 그 시각 */
    private List<AiDraftStop> makeFallbackStops(PlanConditions conditions) {
        List<AiDraftStop> stops = new ArrayList<>();
        int clock = FALLBACK_START_MIN;
        if (conditions.getFixed() != null) {
            for (FixedVisit fixed : conditions.getFixed()) {
                stops.add(makeDraftStop(fixed.getName(), fixed.getTime(), FIXED_STAY_MIN, true));
            }
        }
        List<AiDraftStop> fixedStops = new ArrayList<>(stops);
        if (conditions.getMustVisit() != null) {
            for (String name : conditions.getMustVisit()) {
                while (overlapsFixed(fixedStops, clock, MUST_STAY_MIN) && clock < LAST_MUST_START_MIN) {
                    clock += FALLBACK_STEP_MIN; // 고정 일정과 겹치면 30분씩 뒤로
                }
                stops.add(makeDraftStop(name, Geo.toHhmm(clock), MUST_STAY_MIN, false));
                clock += FALLBACK_GAP_MIN;
            }
        }
        return stops;
    }

    /** [clock, clock+stay) 가 고정 일정 [시각, 시각+머무는 분) 과 겹치는지 */
    private boolean overlapsFixed(List<AiDraftStop> fixedStops, int clock, int stay) {
        for (AiDraftStop fixed : fixedStops) {
            if (fixed.getTime() == null || !fixed.getTime().matches("\\d{2}:\\d{2}")) {
                continue;
            }
            int start = Geo.toMinutes(fixed.getTime());
            if (clock < start + fixed.getStay() && start < clock + stay) {
                return true;
            }
        }
        return false;
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
                                Map<String, List<String>> reasons, int days, Double baseLat, Double baseLng) {
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
            Stop stop = toDraftStop(pick, place, days);
            stop.setMemo(makeMemo(pick.getReason(), reasons.get(place.getId())));
            response.getStops().add(stop);
        }
    }

    /** 일정 칸 메모 = Gemini가 쓴 이유 + 리뷰 근거 ("성수 감성 카페 · 분위기 좋아요 · 리뷰 10개 중 6개"). 둘 다 없으면 null, 200자까지 */
    private String makeMemo(String reason, List<String> evidence) {
        List<String> parts = new ArrayList<>();
        if (hasText(reason)) {
            parts.add(cut(reason.trim(), MAX_REASON_LENGTH));
        }
        if (evidence != null) {
            parts.addAll(evidence);
        }
        return parts.isEmpty() ? null : cut(String.join(" · ", parts), MAX_MEMO_LENGTH);
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
