package com.tripbiseo.plan.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.dev.DevService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.FoundArea;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.live.dto.PlaceTagCount;
import com.tripbiseo.plan.dto.AiConditions;
import com.tripbiseo.plan.dto.AiDraft;
import com.tripbiseo.plan.dto.AiDraftStop;
import com.tripbiseo.plan.dto.FixedVisit;
import com.tripbiseo.plan.dto.PlanChatRequest;
import com.tripbiseo.plan.dto.PlanChatResponse;
import com.tripbiseo.plan.dto.PlanArea;
import com.tripbiseo.plan.dto.PlanConditions;
import com.tripbiseo.plan.dto.PlanFinish;
import com.tripbiseo.plan.dto.PlanGenerateRequest;
import com.tripbiseo.plan.dto.PlanGenerateResponse;
import com.tripbiseo.plan.dto.PlanNote;
import com.tripbiseo.plan.dto.PlanProgress;
import com.tripbiseo.plan.dto.RouteOptionsResponse;
import com.tripbiseo.plan.dto.RouteOrderRequest;
import com.tripbiseo.plan.dto.RouteOrderResponse;
import com.tripbiseo.plan.dto.PlanStay;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PlaceCard;
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

    private static final Logger log = LoggerFactory.getLogger(PlanServiceImpl.class);
    private static final List<String> DEFAULT_TRANSPORTS = List.of("subway", "bus", "walk");
    private static final List<String> KIND_CODES = List.of("food", "cafe", "bar", "tour", "culture", "shop"); // 대화로 더할 수 있는 종류
    private static final List<String> TAG_CODES = List.of("mood", "quiet", "value", "no_wait", "solo");       // 대화로 더할 수 있는 태그
    private static final int MAX_REGION_WORDS = 3;        // 말로 정한 지역에서 중심을 찾는 낱말 수 (카카오 호출 수)
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int MAX_TITLE_LENGTH = 60;
    private static final int MAX_PROMPT_CANDIDATES = 90;
    private static final int MAX_REASON_LENGTH = 40;      // Gemini 이유 한 줄
    private static final int MAX_MEMO_LENGTH = 200;       // trip_stop.memo 저장 규칙
    private static final java.util.regex.Pattern WEAK_EVIDENCE = java.util.regex.Pattern.compile("리뷰 \\d+개 중 (\\d+)개");
    private static final int MIN_EVIDENCE_COUNT = 3;      // 리뷰에서 이만큼은 나와야 근거로 붙임 (11/12)
    private static final int TODAY_START_AFTER_MIN = 60;  // 오늘 출발이면 지금부터 1시간 뒤부터 (PlanRuleMaker와 같게)
    private static final int FALLBACK_START_MIN = 600;    // AI 없이: 10:00부터 2시간 간격
    private static final int FALLBACK_GAP_MIN = 120;
    private static final int FALLBACK_STEP_MIN = 30;      // 고정 일정과 겹치면 꼭 갈 곳을 30분씩 뒤로
    private static final int LAST_MUST_START_MIN = 1260;  // 꼭 갈 곳은 늦어도 21:00 시작
    private static final int MUST_STAY_MIN = 90;
    private static final int FIXED_STAY_MIN = 120;
    private static final String MIDDLE_DAY_START = "10:00"; // 여러 날 여행에서 첫날이 아닌 날의 시작 (PlanRuleMaker와 같음)
    private static final String AI_FAIL_NOTE = "AI가 답하지 않아 고른 종류·평점·거리로 일정을 짰어요"; // AI 대신 규칙으로 짠 안내 (오류 원문은 안 보임)
    private static final String CALL_AHEAD_MARK = "연락 필수"; // 영업시간 원문에 이 말이 있으면 미리 연락해야 하는 곳
    private static final int BOARDING_STAY_MIN = 30;     // 기차역·터미널·공항 고정 일정은 타러 가는 것뿐이라 30분 (11/06)
    private static final int MIN_STAY = 10;
    private static final int MAX_STAY = 600;
    private static final int MAX_NEEDS = 3;          // 칩에 없는 요청은 3개까지 (리뷰 확인이 요청마다 Gemini를 불러서)
    private static final String WORKSHOP_NEED = "4명 이상 앉을 단체석"; // 누구랑 = 워크숍일 때 리뷰로 확인 (11/06)
    private static final String KIDS_NEED = "아이와 가기 좋은 곳";        // 누구랑 = 아이와
    private static final String MOOD_TAG = "mood";                     // 누구랑 = 연인이면 분위기 칩을 더함
    private static final int MAX_NEED_LENGTH = 20;
    private static final double WISH_BONUS = 2.0;    // 가능하면 갈 곳 점수 (태그 0.3·리뷰 확인 1.0보다 크게: 시간이 되면 먼저 들어가게)
    private static final double DONG_BONUS = 1.0; // 고른 동 안의 장소: 평점 차이보다 크게
    private static final int WALK_M_PER_MIN = 75;    // 걷는 속도 4.5km/h (RouteClient와 같음)
    private static final double ROAD_DETOUR = 1.3;   // 직선 거리 → 실제 길 (RouteClient와 같음)
    private static final int MIN_WALK_LIMIT = 5;     // 걷는 분 상한은 5~60분으로
    private static final int MAX_WALK_LIMIT = 60;
    private static final int DEMO_STEP_MS = 900; // 시연 계정 일정 만들기: 진행 단계 하나를 보여 주는 시간
    private static final int BOARDING_BUFFER_MIN = 15; // 기차·버스·비행기는 출발 15분 전 도착 (KTX 19:00 → 18:45, 11/06)
    private static final String MUST = "must";       // 일정 칸 중요도 (trip_stop.importance)
    private static final String MAYBE = "maybe";

    private final AiService aiService;
    private final PlaceService placeService;
    private final PlanCandidateFinder planCandidateFinder; // 후보 장소 찾기 (RAG의 찾기)
    private final RouteOrderer routeOrderer;               // 하루 동선 정리 (직접 고르기 [동선 정리]와 같은 함수)
    private final PlanRuleMaker planRuleMaker;             // AI가 안 될 때 규칙으로 일정 + 숙소 칸 (11/03)
    private final ReviewNeedChecker reviewNeedChecker;     // 칩에 없는 요청을 후보 리뷰로 확인 (11/04)
    private final PlanLimitChecker planLimitChecker;       // 끝 장소·도착 마감, 걷는 시간 상한 (11/04)
    private final PlanQuestionMaker planQuestionMaker;     // 대화의 다음 질문 (11/04)
    private final PlanWordGuesser planWordGuesser;         // Gemini가 안 될 때 낱말로만 조건 (11/04)
    private final DevService devService;                   // 시연 계정인지 (11/08)
    private final DemoScenario demoScenario;               // 시연 계정이면 이 시나리오 일정 (11/08)

    /** 사용자 말 → 여행 조건. 보낸 조건은 남기고 Gemini가 새로 뽑은 값만 덮어씀. Gemini 실패는 오류 그대로 (API.md) */
    @Override
    public PlanChatResponse chatPlan(PlanChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw fail(400, "메시지를 1~300자로 입력해 주세요.");
        }
        PlanConditions conditions = request.getConditions() == null ? new PlanConditions() : request.getConditions();
        List<String> asked = planQuestionMaker.askedOf(conditions);
        AiConditions answer = askConditions(conditions, message);
        mergeConditions(conditions, answer);
        fillDefaults(conditions);

        PlanChatResponse response = new PlanChatResponse();
        if (answer == null) {
            // Gemini도 안 되고 낱말로도 못 읽음: 한 말은 일정 만들 때 넘어가니 그렇게 말하고, 대화는 다음 질문으로 이어 감
            response.setReply("말한 내용은 일정을 만들 때 같이 볼게요.");
            response.setQuestion(planQuestionMaker.textOf(addNextQuestion(conditions, asked)));
        } else if (!planQuestionMaker.hasAnyCondition(answer)) {
            // 조건이 없는 말(인사·날씨 등): Gemini 답 대신 늘 같은 안내 + 방금 물은 질문을 다시 (답이 이미 있으면 다음 질문) (11/04)
            response.setReply(planQuestionMaker.nothingText());
            response.setQuestion(planQuestionMaker.textOf(planQuestionMaker.pickRepeat(conditions, asked)));
        } else {
            boolean hasReply = answer.getReply() != null && !answer.getReply().isBlank();
            String reply = hasReply ? answer.getReply() : "조건을 정리했어요. 확인해 주세요.";
            response.setReply(answer.getFinish() == null ? reply : reply + " " + makeFinishText(conditions));
            response.setQuestion(planQuestionMaker.textOf(addNextQuestion(conditions, asked)));
        }
        conditions.setAsked(asked);
        response.setConditions(conditions);
        return response;
    }

    /** 다음 질문을 서버가 골라 물어본 기록에 더함 (다 물었으면 null) */
    private String addNextQuestion(PlanConditions conditions, List<String> asked) {
        String next = planQuestionMaker.pickNext(conditions, asked);
        if (next != null) {
            asked.add(next);
        }
        return next;
    }

    /**
     * 말 → 조건 (Gemini, 생각 단계 LOW로 빨리). Gemini가 안 되면(시간 초과·키 없음) 낱말로 종류·장소만 읽고,
     * 그것도 없으면 null (chatPlan이 "일정 만들 때 같이 볼게요"로 답함)
     */
    private AiConditions askConditions(PlanConditions conditions, String message) {
        try {
            return aiService.askJsonQuick(makeChatPrompt(conditions, message), AiConditions.class);
        } catch (ApiException e) {
            log.info("대화 조건을 Gemini로 못 읽어 낱말로 읽음 ({}): {}", e.getStatus(), e.getMessage());
            AiConditions guessed = planWordGuesser.guess(message);
            return planQuestionMaker.hasAnyCondition(guessed) ? guessed : null;
        }
    }

    /** 끝 장소를 말했을 때 되묻는 말: 서버가 경로로 계산한 마지막 일정 끝 시각 (고른 지역·출발지 → 끝 장소) */
    private String makeFinishText(PlanConditions conditions) {
        double[] from = null;
        if (conditions.getAreaLat() != null) {
            from = new double[] {conditions.getAreaLat(), conditions.getAreaLng()};
        } else if (conditions.getOriginLat() != null) {
            from = new double[] {conditions.getOriginLat(), conditions.getOriginLng()};
        }
        if (from == null) {
            return "지역을 고르면 마지막 일정이 몇 시에 끝나야 하는지 계산해 드릴게요.";
        }
        PlanFinish finish = planLimitChecker.findFinish(conditions, from, conditions.getTransports());
        return finish == null ? "" : planLimitChecker.makeFinishText(finish) + ".";
    }

    /** 조건 → 일정 초안 (진행 알림 없이: 예전 주소 /generate) */
    @Override
    public PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request) {
        return generatePlan(memberId, request, step -> { }); // 진행 알림은 받아도 아무것도 안 함
    }

    /** 조건 → 일정 초안: 후보 찾기 → 리뷰로 요청 확인 → Gemini 고르기(실패하면 규칙) → 검사 → 실제 장소로. 단계가 끝날 때마다 progress로 알림 */
    @Override
    public PlanGenerateResponse generatePlan(long memberId, PlanGenerateRequest request, Consumer<PlanProgress> progress) {
        PlanConditions conditions = request.getConditions();
        if (conditions == null) {
            throw fail(400, "여행 조건이 없어요.");
        }
        if (devService.isDemoAccount(memberId)) {
            return makeDemoDraft(conditions, progress); // 시연 계정은 발표 시나리오 일정 (11/08 주형)
        }
        boolean isMoved = moveToTomorrowIfLate(conditions);
        PlanGenerateResponse response = makeEmptyDraft(conditions);
        addBoardingBuffer(conditions, response.getNotes());
        if (isMoved) {
            response.getNotes().add(makeNote("오늘은 늦어서 내일 일정으로 짰어요", null));
        }
        int days = (int) ChronoUnit.DAYS.between(LocalDate.parse(response.getStartDate()), LocalDate.parse(response.getEndDate())) + 1;
        String region = conditions.getRegion() == null ? "" : conditions.getRegion();
        boolean isNearMe = conditions.getAreaLat() == null && planCandidateFinder.isNearMe(region); // 지역을 골랐으면 내 위치는 안 씀
        Double baseLat = isNearMe ? request.getLat() : null; // 다른 지역을 말했으면 내 위치는 쓰지 않음
        Double baseLng = isNearMe ? request.getLng() : null;

        Map<String, Place> found = findCandidates(conditions, region, isNearMe, baseLat, baseLng, response.getNotes());
        int avoided = removeAvoided(conditions, found);
        progress.accept(makeProgress("candidates", "done", 20, "장소 후보 " + found.size() + "곳 찾기",
                avoided > 0 ? "피하고 싶은 곳·종류 " + avoided + "곳 뺌" : null));
        Map<String, PlaceTagCount> tagCounts = planCandidateFinder.countTags(found);
        Map<String, Double> scores = planCandidateFinder.scoreOf(found, tagCounts, conditions.getTags());
        Map<String, List<String>> reasons = planCandidateFinder.reasonsOf(found, tagCounts, conditions.getTags());
        addWishBonus(conditions, found, scores);
        checkNeeds(conditions, found, scores, reasons, response, progress);
        Map<String, Place> candidates = planCandidateFinder.sortByScore(found, scores); // 고른 태그가 많은 곳부터
        // 끝 장소가 있으면 마지막 날 일정이 끝나야 하는 시각을 먼저 계산 (칸 고르기에 씀)
        PlanFinish finish = planLimitChecker.findFinish(conditions, startPoint(conditions, baseLat, baseLng, found), transportsOf(conditions));

        List<AiDraftStop> filled = chooseStops(conditions, request.getMessages(), candidates, scores, reasons, days, response, finish, progress);
        addPickedStops(response, filled, candidates, reasons, days, baseLat, baseLng, conditions);
        removeRepeatedStops(response.getStops()); // 같은 곳 두 번 (11/06)
        removeAfterBoarding(response.getStops(), days); // KTX 탄 뒤 일정 (11/06)
        addKeptAndSort(conditions, response, days);
        planRuleMaker.fixTravelTimes(response); // 순서를 바꾼 뒤 이동 시간이 모자란 칸을 밀거나 줄임 (11/12)
        planRuleMaker.addStayStops(response, conditions, days);
        if (response.getStops().isEmpty()) {
            boolean isMissingLocation = isNearMe && request.getLat() == null;
            throw fail(422, isMissingLocation ? "내 위치를 받지 못했어요. 브라우저 위치 권한을 허용하거나 지역 이름을 말해 주세요."
                                              : "일정에 넣을 장소를 찾지 못했어요. 가고 싶은 곳을 조금 더 자세히 말해 주세요.");
        }
        applyLimits(conditions, response, days, finish, progress);
        setShortTitle(conditions, response, days);
        addWishNotes(conditions, response);
        addMissedMust(conditions, response);
        reportFixed(conditions, progress);
        // day → time 순 (sort에 넘기는 규칙: day가 같으면 time 글자 순, "09:00" < "10:00")
        response.getStops().sort((first, second) -> first.getDay().equals(second.getDay())
                ? first.getTime().compareTo(second.getTime()) : first.getDay() - second.getDay());
        return response;
    }

    /** [동선 최적화] 이동 시간이 짧은 순서 후보 */
    @Override
    public RouteOptionsResponse getRouteOptions(RouteOrderRequest request) {
        if (request.getStops() == null) {
            throw fail(400, "일정이 없어요.");
        }
        double[] start = request.getStartLat() == null ? null : new double[] {request.getStartLat(), request.getStartLng()};
        LocalDate date = request.getDate() == null ? null : LocalDate.parse(request.getDate());
        return routeOrderer.getOptions(request.getStops(), start, date, request.getTransports());
    }

    /** [동선 정리] 하루 일정의 새 순서 (AI 일정과 같은 함수) */
    @Override
    public RouteOrderResponse getRouteOrder(RouteOrderRequest request) {
        if (request.getStops() == null) {
            throw fail(400, "일정이 없어요.");
        }
        double[] start = request.getStartLat() == null ? null : new double[] {request.getStartLat(), request.getStartLng()};
        LocalDate date = request.getDate() == null ? null : LocalDate.parse(request.getDate());
        RouteOrderResponse response = new RouteOrderResponse();
        response.setOrder(routeOrderer.getOrder(request.getStops(), start, date));
        List<Integer> before = new ArrayList<>();
        for (int i = 0; i < request.getStops().size(); i++) {
            before.add(i);
        }
        boolean hasPoints = !response.getOrder().isEmpty() && request.getStops().get(0).getLat() != null;
        response.setBeforeMeters(hasPoints ? routeOrderer.lengthOf(request.getStops(), before, start) : 0);
        response.setAfterMeters(hasPoints ? routeOrderer.lengthOf(request.getStops(), response.getOrder(), start) : 0);
        return response;
    }

    /** 담은 곳을 일정에 넣고 동선 정리: 담은 곳은 잠깐 고정 칸으로 봐서 순서·시각이 안 바뀌게 */
    private void addKeptAndSort(PlanConditions conditions, PlanGenerateResponse response, int days) {
        List<Stop> kept = conditions.getKeptStops() == null ? List.of() : conditions.getKeptStops();
        Map<Stop, Boolean> wasFixed = new java.util.IdentityHashMap<>(); // 같은 칸 객체로 찾아요 (내용이 같아도 다른 칸)
        for (Stop stop : kept) {
            wasFixed.put(stop, stop.getFixed());
            stop.setFixed(true);
            stop.setDay(stop.getDay() == null ? 1 : stop.getDay());
            stop.setTime(stop.getTime() == null ? conditions.getStartTime() : stop.getTime());
            stop.setImportance(MUST);
            response.getStops().add(stop);
        }
        sortRoutes(conditions, response, days);
        for (Stop stop : kept) {
            stop.setFixed(Boolean.TRUE.equals(wasFixed.get(stop)));
        }
    }

    /** 날마다 동선 정리: 시각 칸은 그대로, 장소 순서만 가까운 차례로 */
    private void sortRoutes(PlanConditions conditions, PlanGenerateResponse response, int days) {
        List<Stop> sorted = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            List<Stop> dayStops = new ArrayList<>();
            for (Stop stop : response.getStops()) {
                if (stop.getDay() == day) {
                    dayStops.add(stop);
                }
            }
            dayStops.sort((first, second) -> first.getTime().compareTo(second.getTime())); // 시각 글자 순
            LocalDate date = LocalDate.parse(response.getStartDate()).plusDays(day - 1);
            sorted.addAll(routeOrderer.sortDay(dayStops, planRuleMaker.realStartOf(conditions, response.getStartDate(), day), date));
        }
        response.setStops(sorted);
    }

    // ---------------------------------------------------------------- 진행 단계 (11/04)

    /** 칩에 없는 요청을 리뷰로 확인 (요청이 없으면 이 단계는 건너뜀) */
    private void checkNeeds(PlanConditions conditions, Map<String, Place> found, Map<String, Double> scores,
                            Map<String, List<String>> reasons, PlanGenerateResponse response, Consumer<PlanProgress> progress) {
        if (conditions.getNeeds() == null || conditions.getNeeds().isEmpty()) {
            return;
        }
        String text = "리뷰로 요청 확인 (" + String.join(", ", conditions.getNeeds()) + ")";
        progress.accept(makeProgress("reviews", "now", 25, text, null));
        int before = found.size();
        reviewNeedChecker.check(conditions, found, scores, reasons, response.getNotes()); // 안 맞는 곳은 빼고, 맞는 곳은 점수·리뷰 근거
        int removed = before - found.size();
        progress.accept(makeProgress("reviews", "done", 40, text, removed > 0 ? "안 맞는다는 리뷰가 있는 " + removed + "곳 뺌" : "뺀 곳 없음"));
    }

    /** 오늘 하루 일정인데 남은 칸이 없으면(밤늦게) 내일로 옮김. 예약(고정 일정)이 있으면 그날을 지켜야 해서 안 옮김 */
    private boolean moveToTomorrowIfLate(PlanConditions conditions) {
        LocalDate today = Geo.now().toLocalDate();
        String start = hasText(conditions.getStartDate()) ? conditions.getStartDate() : today.toString();
        boolean hasFixed = conditions.getFixed() != null && !conditions.getFixed().isEmpty();
        if (!start.equals(today.toString()) || hasFixed || !planRuleMaker.isTodayOver(conditions)) {
            return false;
        }
        String tomorrow = today.plusDays(1).toString();
        boolean isOneDay = !hasText(conditions.getEndDate()) || conditions.getEndDate().equals(start);
        conditions.setStartDate(tomorrow);
        if (isOneDay) {
            conditions.setEndDate(tomorrow);
        }
        return true;
    }

    /** Gemini 고르기(실패하면 규칙) → 칸 검사·빈 칸 채우기. 두 단계를 알림 */
    private List<AiDraftStop> chooseStops(PlanConditions conditions, List<String> messages, Map<String, Place> candidates,
                                          Map<String, Double> scores, Map<String, List<String>> reasons, int days,
                                          PlanGenerateResponse response, PlanFinish finish, Consumer<PlanProgress> progress) {
        String aiText = aiService.isReady() ? "AI가 칸마다 장소 고르기" : "평점·거리로 장소 고르기 (AI 연결 안 됨)";
        progress.accept(makeProgress("ai", "now", 45, aiText, null));
        List<AiDraftStop> picks = pickStops(conditions, messages, candidates, reasons, days, response, makeLimitRule(conditions, finish));
        addMissingMustPicks(conditions, picks, days); // Gemini가 꼭 갈 곳을 빠뜨리면 서버가 넣음 (11/06)
        if (hasNote(response, AI_FAIL_NOTE)) { // AI가 답하지 않았으면 성공처럼 보이지 않게 (11/13)
            progress.accept(makeProgress("ai", "done", 80, "AI가 답하지 않아 평점·거리로 고르기", "AI 없이 종류·평점·거리로 채워요"));
        } else {
            progress.accept(makeProgress("ai", "done", 80, aiText, picks.size() + "칸 고름"));
        }

        List<String> warnings = new ArrayList<>();
        Integer lastDayEndMin = finish == null ? null : finish.getLastEndMin();
        List<AiDraftStop> filled = planRuleMaker.fillSlots(conditions, candidates, scores, days, response.getStartDate(), picks, warnings,
                lastDayEndMin, maxWalkMetersOf(conditions));
        for (String warning : warnings) {
            response.getNotes().add(makeNote(warning, null));
        }
        String detail = warnings.isEmpty() ? "모두 통과" : String.join(" · ", warnings);
        progress.accept(makeProgress("rules", "done", 90, "영업시간·종류·겹침 검사", detail));
        return filled;
    }

    /** 끝 장소에 맞추고(늦으면 뒤에서 뺌) 끝 칸 더하기 → 걷는 시간 확인. 조건이 있을 때만, 단계마다 알림 */
    private void applyLimits(PlanConditions conditions, PlanGenerateResponse response, int days, PlanFinish finish,
                             Consumer<PlanProgress> progress) {
        List<String> transports = transportsOf(conditions);
        if (finish != null) {
            String detail = planLimitChecker.fitFinish(response, finish, days, transports, response.getNotes());
            progress.accept(makeProgress("finish", "done", 93, finish.getName() + " " + finish.getTime() + " 도착 맞추기", detail));
        }
        if (conditions.getMaxWalkMin() != null) {
            String detail = planLimitChecker.checkWalk(response, conditions.getMaxWalkMin(), transports, response.getNotes());
            progress.accept(makeProgress("walk", "done", 94, "걷는 시간 확인 (한 번에 " + conditions.getMaxWalkMin() + "분까지)", detail));
        }
    }

    /** 프롬프트에 더할 제한 규칙 (끝 장소·걷는 시간·가능하면 갈 곳). 없으면 빈 글 */
    private String makeLimitRule(PlanConditions conditions, PlanFinish finish) {
        StringBuilder rule = new StringBuilder();
        if (finish != null) {
            rule.append("- 마지막 날은 ").append(Geo.toHhmm(finish.getLastEndMin())).append("까지 일정이 끝나야 해 (")
                .append(finish.getName()).append("로 가야 함). 그 뒤 칸은 비워.\n");
        }
        if (conditions.getMaxWalkMin() != null) {
            rule.append("- 칸 사이는 걸어서 ").append(conditions.getMaxWalkMin()).append("분 안쪽인 가까운 곳으로 골라.\n");
        }
        if (conditions.getWishVisit() != null && !conditions.getWishVisit().isEmpty()) {
            rule.append("- wishVisit(가능하면 갈 곳)은 시간이 맞으면 알맞은 칸에 넣어 (칸 종류가 달라도 돼). 안 맞으면 빼.\n");
        }
        return rule.toString();
    }

    /** 피하고 싶은 곳(이름)과 빼 달라는 종류는 후보에서 뺌 (종류는 꼭·가능하면 갈 곳으로 직접 말한 곳이면 남김). 뺀 수를 돌려줌 */
    private boolean isKeptPlace(Place place, PlanConditions conditions) {
        if (conditions.getKeptStops() == null) {
            return false;
        }
        for (Stop stop : conditions.getKeptStops()) {
            if (place.getId().equals(stop.getPlaceId()) || isSameName(place.getName(), stop.getName())) {
                return true;
            }
        }
        return false;
    }

    private int removeAvoided(PlanConditions conditions, Map<String, Place> found) {
        List<String> avoidKinds = conditions.getAvoidKinds() == null ? List.of() : conditions.getAvoidKinds();
        List<String> removing = new ArrayList<>();
        for (Place place : found.values()) {
            boolean isNamed = planCandidateFinder.isMustVisit(place, conditions.getMustVisit())
                    || planCandidateFinder.isMustVisit(place, conditions.getWishVisit());
            boolean isAvoidKind = avoidKinds.contains(place.getKind()) && !isNamed;
            boolean isKept = isKeptPlace(place, conditions);
            if (isKept || isAvoidKind || planCandidateFinder.isMustVisit(place, conditions.getAvoidVisit())) { // isMustVisit: 이름이 서로 포함되면 같은 곳
                removing.add(place.getId());
            }
        }
        for (String placeId : removing) {
            found.remove(placeId);
        }
        return removing.size();
    }

    /** 동을 골랐는데 그 안에 장소가 없으면 구 전체로 넓혔다고 알림 */
    private void addDongNote(PlanConditions conditions, List<PlanNote> notes) {
        boolean hasDong = false;
        for (PlanArea area : conditions.getAreas()) {
            hasDong = hasDong || (area.getDongs() != null && !area.getDongs().isEmpty());
        }
        if (hasDong && conditions.getDongPlaceIds().isEmpty()) {
            notes.add(makeNote("고른 동에 장소가 적어 구 전체로 넓혔어요", null));
        }
    }

    /** 가능하면 갈 곳·고른 동 안의 장소는 점수를 더해 먼저 들어가게 */
    private void addWishBonus(PlanConditions conditions, Map<String, Place> found, Map<String, Double> scores) {
        if (conditions.getDongPlaceIds() != null) {
            for (String placeId : conditions.getDongPlaceIds()) {
                scores.put(placeId, scores.getOrDefault(placeId, 0.0) + DONG_BONUS);
            }
        }
        if (conditions.getWishVisit() == null) {
            return;
        }
        for (Place place : found.values()) {
            if (planCandidateFinder.isMustVisit(place, conditions.getWishVisit())) {
                scores.put(place.getId(), scores.getOrDefault(place.getId(), 0.0) + WISH_BONUS);
            }
        }
    }

    /** 가능하면 갈 곳 중 일정에 못 넣은 곳 안내 */
    private void addWishNotes(PlanConditions conditions, PlanGenerateResponse response) {
        if (conditions.getWishVisit() == null) {
            return;
        }
        for (String name : conditions.getWishVisit()) {
            if (!hasStopNamed(response.getStops(), name)) {
                response.getNotes().add(makeNote("가능하면 갈 곳 '" + name + "'은(는) 시간이 모자라거나 위치를 못 찾아 못 넣었어요",
                        placeService.getNaverUrl(name, null)));
            }
        }
    }

    /** 만든 일정 이름은 짧게: "문래동 하루", "영등포구 1박 2일". 지역을 모르면 Gemini가 지은 이름 그대로 */
    private void setShortTitle(PlanConditions conditions, PlanGenerateResponse response, int days) {
        String region = conditions.getRegion();
        if (region == null || region.isBlank()) {
            return;
        }
        String[] words = region.trim().split("\\s+");
        String place = words[words.length - 1].replaceAll("\\d+가$", ""); // "문래동1가" → "문래동"
        response.setTitle(place + (days <= 1 ? " 하루" : " " + (days - 1) + "박 " + days + "일"));
    }

    /** 꼭 갈 곳 중 일정에 못 넣은 곳을 응답에 담음 (화면이 맨 위에 보여 주고 다시 넣게) */
    private void addMissedMust(PlanConditions conditions, PlanGenerateResponse response) {
        if (conditions.getMustVisit() == null) {
            return;
        }
        for (String name : conditions.getMustVisit()) {
            if (!hasStopNamed(response.getStops(), name)) {
                response.getMissed().add(toMissedCard(name, conditions));
            }
        }
    }

    /** 이름으로 장소 카드 찾기. 못 찾거나 외부 검색이 실패하면 이름만 */
    private PlaceCard toMissedCard(String name, PlanConditions conditions) {
        try {
            Place place = placeService.getPlaceByName(name, conditions.getAreaLat(), conditions.getAreaLng());
            if (place != null) {
                return placeService.getPlaceCard(place, null, null);
            }
        } catch (ApiException e) {
            log.info("못 넣은 꼭 갈 곳 찾기 실패: {}", e.getMessage());
        }
        PlaceCard card = new PlaceCard();
        card.setName(name);
        return card;
    }

    /** 일정에 그 이름이 들어간 칸이 있는지 ("한강" → "뚝섬한강공원"도 같은 곳으로 봄) */
    private boolean hasStopNamed(List<Stop> stops, String name) {
        for (Stop stop : stops) {
            if (isSameName(stop.getName(), name)) {
                return true;
            }
        }
        return false;
    }

    /** 이동 시간을 잴 출발점: 고른 지역 → 내 위치 → 출발지 → 첫 후보 (모르면 null) */
    private double[] startPoint(PlanConditions conditions, Double baseLat, Double baseLng, Map<String, Place> found) {
        if (conditions.getAreaLat() != null) {
            return new double[] {conditions.getAreaLat(), conditions.getAreaLng()};
        }
        if (baseLat != null && baseLng != null) {
            return new double[] {baseLat, baseLng};
        }
        if (conditions.getOriginLat() != null) {
            return new double[] {conditions.getOriginLat(), conditions.getOriginLng()};
        }
        Place first = found.isEmpty() ? null : found.values().iterator().next();
        return first == null ? null : new double[] {first.getLat(), first.getLng()};
    }

    /** 걷는 분 상한 → 칸 사이 직선 거리 상한 (길은 직선보다 1.3배 돌아가서 나눔). 상한이 없으면 null */
    private Integer maxWalkMetersOf(PlanConditions conditions) {
        if (conditions.getMaxWalkMin() == null) {
            return null;
        }
        return (int) (conditions.getMaxWalkMin() * WALK_M_PER_MIN / ROAD_DETOUR);
    }

    private List<String> transportsOf(PlanConditions conditions) {
        return conditions.getTransports() == null || conditions.getTransports().isEmpty() ? DEFAULT_TRANSPORTS : conditions.getTransports();
    }

    /** 시간 고정 일정이 있으면 "18:30 광명역 시간에 맞추기" (고정 칸은 그 시각에 넣고 겹치는 칸은 비움) */
    private void reportFixed(PlanConditions conditions, Consumer<PlanProgress> progress) {
        if (conditions.getFixed() == null || conditions.getFixed().isEmpty()) {
            return;
        }
        boolean isManyDays = conditions.getEndDate() != null && !conditions.getEndDate().equals(conditions.getStartDate());
        List<String> names = new ArrayList<>();
        for (FixedVisit fixed : conditions.getFixed()) {
            names.add((isManyDays ? dayOf(fixed) + "일차 " : "") + fixed.getTime() + " " + fixed.getName());
        }
        progress.accept(makeProgress("fixed", "done", 95, String.join(", ", names) + " 시간에 맞추기", null));
    }

    /** 시연 계정: 진행 단계를 평소처럼 몇 초 보여 준 뒤 시나리오 일정(DemoScenario)을 돌려줌 */
    private PlanGenerateResponse makeDemoDraft(PlanConditions conditions, Consumer<PlanProgress> progress) {
        String[][] steps = {
            { "candidates", "20", "장소 후보 찾기" },
            { "reviews", "40", "리뷰로 단체석·분위기 확인" },
            { "ai", "80", "AI가 일정 고르기" },
            { "fixed", "95", "1일차 19:00 회식 · 2일차 12:30 스시도 · 16:00 버스 시간에 맞추기" }, // 11/12 촬영 시나리오
        };
        for (String[] step : steps) {
            waitDemoStep();
            progress.accept(makeProgress(step[0], "done", Integer.parseInt(step[1]), step[2], null));
        }
        return demoScenario.makeDraft(conditions);
    }

    private void waitDemoStep() {
        try {
            Thread.sleep(DEMO_STEP_MS); // 로딩 화면이 한 번에 지나가지 않게
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // 멈추라는 신호는 그대로 남겨 둠
        }
    }

    private PlanProgress makeProgress(String step, String state, int percent, String text, String detail) {
        PlanProgress item = new PlanProgress();
        item.setStep(step);
        item.setState(state);
        item.setPercent(percent);
        item.setText(text);
        item.setDetail(detail);
        return item;
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
             + "\"mustVisit\": [\"이번 말에서 새로 나온 꼭 갈 장소\"], \"fixed\": [{\"name\": \"이번 말에서 새로 나온 시간이 정해진 일정\", \"time\": \"HH:mm\", \"day\": 몇째 날(1부터, 말 안 했으면 null)}], "
             + "\"kinds\": [\"새로 원한 장소 종류 코드\"], \"tags\": [\"새로 원한 분위기 코드\"], \"needs\": [\"새로 말한 장소 요청\"], "
             + "\"wishVisit\": [\"가능하면 갈 곳\"], \"mustKinds\": [\"꼭 넣을 종류 코드\"], \"avoidKinds\": [\"빼 달라는 종류 코드\"], \"avoidVisit\": [\"피하고 싶은 곳 이름\"], "
             + "\"finish\": {\"name\": \"여행이 끝나는 곳\", \"time\": \"HH:mm\"}, \"maxWalkMin\": 정수, \"style\": \"여행 스타일 한 줄\"}\n"
             + "mustVisit·wishVisit·fixed·kinds·tags·needs·mustKinds·avoidKinds·avoidVisit는 이번 말에서 새로 나온 것만 적어(이전 것은 서버가 남겨 둬). 상호명은 사용자가 말한 그대로.\n"
             + "kinds 코드: food 밥집, cafe 카페·디저트, bar 술집, tour 관광·산책, culture 전시·문화, shop 쇼핑. 그 종류에 가고 싶다고 했을 때만.\n"
             + "tags 코드: mood 분위기 좋은, quiet 조용한, value 가성비, no_wait 웨이팅 없는(웨이팅 싫다 포함), solo 혼밥. 그런 곳을 원한다고 했을 때만.\n"
             + "needs: kinds·tags 코드로 못 담는 장소 조건을 15자 이내 짧은 말로 (예: 4명이 앉을 자리, 주차되는 곳, 아이랑 가기 좋은). 장소 이름·시각·이동 수단·걷는 시간은 넣지 마.\n"
             + "꼭·가능하면: 장소를 꼭·반드시·무조건·가야 해라고 하면 mustVisit, 가능하면·되면·시간 남으면·여유 되면이라고 하거나 애매하면 wishVisit. "
             + "장소 이름이 아니라 종류(선물 사기·기념품 → shop)를 꼭 하고 싶다고 하면 mustKinds 코드(가능하면이면 kinds).\n"
             + "피하고 싶은 것: 종류를 별로·싫다·빼 줘라고 하면 avoidKinds 코드, 특정 장소 이름이면 avoidVisit. 웨이팅 싫어는 tags의 no_wait, 종류도 이름도 아닌 건 needs.\n"
             + "흔한 말 예: \"카페는 별로 안 가고 싶어\" → avoidKinds [cafe] / \"술은 안 마셔\" → avoidKinds [bar] / \"쇼핑은 관심 없어\" → avoidKinds [shop] / "
             + "\"롯데월드는 가 봐서 빼 줘\" → avoidVisit [롯데월드] / \"사람 많은 곳은 싫어\" → needs [붐비지 않는 곳] / "
             + "\"매운 거 못 먹어\" → needs [안 매운 메뉴] / \"계단 많은 곳은 힘들어\" → needs [계단 적은 곳] / \"줄 서는 거 싫어\" → tags [no_wait].\n"
             + "finish: 기차·비행기·버스를 타야 하거나 집에 가야 해서 여행이 끝나는 곳. name은 그 역·터미널·공항 이름, time은 거기 도착해야 하는 시각(출발 15분 전). finish는 fixed에 넣지 마.\n"
             + "maxWalkMin: 오래 걷기 싫다·다리·무릎이 불편하다고 하면 한 번에 걸을 수 있는 분. 숫자를 말하면 그 값, 아니면 15.";
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
                appendLine(text, "fixed", dayOf(fixed) + "일차 " + fixed.getTime() + " " + fixed.getName());
            }
        }
        appendLine(text, "style", conditions.getStyle());
        if (conditions.getAreas() != null && conditions.getAreas().size() > 1) {
            text.append("여러 구를 돌아요. 칸마다 서버가 정한 구의 후보를 고르세요\n");
        }
        appendLine(text, "kinds(이 종류 위주로, 하루 칸에 이미 반영)", conditions.getKinds() == null ? null : toKindNames(conditions.getKinds()));
        appendLine(text, "tags(이런 곳 우선)", String.join(",", planCandidateFinder.toTagNames(conditions.getTags())));
        appendLine(text, "needs(장소 요청, 서버가 리뷰로 확인함)", conditions.getNeeds() == null ? null : String.join(",", conditions.getNeeds()));
        appendLine(text, "wishVisit(가능하면 갈 곳)", conditions.getWishVisit() == null ? null : String.join(",", conditions.getWishVisit()));
        appendLine(text, "mustKinds(꼭 넣을 종류)", conditions.getMustKinds() == null ? null : toKindNames(conditions.getMustKinds()));
        appendLine(text, "avoidKinds(빼 달라는 종류)", conditions.getAvoidKinds() == null ? null : toKindNames(conditions.getAvoidKinds()));
        appendLine(text, "avoidVisit(피하고 싶은 곳, 고르지 마)", conditions.getAvoidVisit() == null ? null : String.join(",", conditions.getAvoidVisit()));
        FixedVisit finish = conditions.getFinish();
        appendLine(text, "finish(여행이 끝나는 곳, 이 시각까지 도착)", finish == null ? null : finish.getName() + " " + finish.getTime());
        appendLine(text, "maxWalkMin(한 번에 걷는 분 상한)", conditions.getMaxWalkMin() == null ? null : String.valueOf(conditions.getMaxWalkMin()));
        // 출발지 이름은 보내지 않음: "현재 위치"면 집 주소일 수 있어서 (11/13). 출발지는 서버가 좌표로만 씀
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
        mergeLimits(conditions, answer);
        if (hasText(answer.getStyle())) {
            conditions.setStyle(answer.getStyle());
        }
    }

    private void mergeLists(PlanConditions conditions, AiConditions answer) {
        if (answer.getTransports() != null && !answer.getTransports().isEmpty()) {
            conditions.setTransports(answer.getTransports());
        }
        // 꼭 갈 곳·고정 일정·종류·태그는 새로 말한 것을 더함 (빼기는 화면의 조건 카드에서 사용자가 직접)
        mergeVisits(conditions, answer);
        conditions.setKinds(addNew(conditions.getKinds(), answer.getKinds(), KIND_CODES));
        conditions.setTags(addNew(conditions.getTags(), answer.getTags(), TAG_CODES));
        conditions.setNeeds(cutNeeds(addNew(conditions.getNeeds(), answer.getNeeds(), null)));
        mergeKindLimits(conditions, answer);
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

    /** 끝 장소·걷는 시간 상한 (11/04). 고정 일정을 더한 뒤에 해야 Gemini가 끝 장소를 fixed에도 넣었을 때 뺄 수 있음 */
    private void mergeLimits(PlanConditions conditions, AiConditions answer) {
        FixedVisit finish = answer.getFinish();
        if (finish != null && hasText(finish.getName()) && hasText(finish.getTime())) {
            conditions.setFinish(finish);
            conditions.setFixed(removeFixedNamed(conditions.getFixed(), finish.getName())); // 고정 일정에도 넣었으면 뺌
        }
        if (answer.getMaxWalkMin() != null) {
            conditions.setMaxWalkMin(Math.max(MIN_WALK_LIMIT, Math.min(MAX_WALK_LIMIT, answer.getMaxWalkMin())));
        }
    }

    /** 꼭 갈 곳·가능하면 갈 곳: 새로 말한 쪽으로 옮김 (같은 이름이 두 목록에 같이 있지 않게) */
    private void mergeVisits(PlanConditions conditions, AiConditions answer) {
        List<String> must = removeAll(addNew(conditions.getMustVisit(), answer.getMustVisit(), null), answer.getWishVisit());
        List<String> wish = removeAll(addNew(conditions.getWishVisit(), answer.getWishVisit(), null), answer.getMustVisit());
        List<String> avoid = addNew(conditions.getAvoidVisit(), answer.getAvoidVisit(), null);
        avoid = removeAll(removeAll(avoid, answer.getMustVisit()), answer.getWishVisit()); // 다시 가고 싶다고 하면 풀어 줌
        conditions.setMustVisit(removeAll(must, answer.getAvoidVisit())); // 피하고 싶다고 하면 갈 곳에서 뺌
        conditions.setWishVisit(removeAll(wish, answer.getAvoidVisit()));
        conditions.setAvoidVisit(avoid);
    }

    /** 꼭 넣을 종류는 kinds에도 넣고, 빼 달라는 종류는 kinds·꼭 넣을 종류에서 뺌 (kinds는 하나 이상 남김) */
    private void mergeKindLimits(PlanConditions conditions, AiConditions answer) {
        conditions.setMustKinds(addNew(conditions.getMustKinds(), answer.getMustKinds(), KIND_CODES));
        conditions.setKinds(addNew(conditions.getKinds(), answer.getMustKinds(), KIND_CODES));
        List<String> avoid = addNew(conditions.getAvoidKinds(), answer.getAvoidKinds(), KIND_CODES);
        avoid = removeAll(removeAll(avoid, answer.getKinds()), answer.getMustKinds()); // 다시 가고 싶다고 하면 풀어 줌
        conditions.setAvoidKinds(avoid);
        conditions.setMustKinds(removeAll(conditions.getMustKinds(), avoid));
        List<String> kinds = removeAll(planRuleMaker.chosenKindsOf(conditions), avoid);
        if (kinds != null && !kinds.isEmpty()) {
            conditions.setKinds(kinds);
        }
    }

    /** list에서 removing에 든 값을 뺀 새 목록 (둘 중 하나가 null이면 list 그대로) */
    private List<String> removeAll(List<String> list, List<String> removing) {
        if (list == null || removing == null) {
            return list;
        }
        List<String> result = new ArrayList<>();
        for (String value : list) {
            if (!removing.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /** 고정 일정에서 그 이름을 뺀 목록 */
    private List<FixedVisit> removeFixedNamed(List<FixedVisit> fixedList, String name) {
        if (fixedList == null) {
            return null;
        }
        List<FixedVisit> result = new ArrayList<>();
        for (FixedVisit fixed : fixedList) {
            if (!name.equals(fixed.getName())) {
                result.add(fixed);
            }
        }
        return result;
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
        addCompanionNeeds(conditions);
    }

    /**
     * 누구랑에 맞는 리뷰 확인 요청을 더함 (11/06). 리뷰에서 그 말이 보이는 곳이 점수를 더 받아요 (ReviewNeedChecker).
     * 워크숍: 단체석 · 아이와: 아이와 가기 좋은 곳 · 연인: 분위기 칩
     */
    private void addCompanionNeeds(PlanConditions conditions) {
        String companion = conditions.getCompanion();
        if ("워크숍".equals(companion)) {
            addNeed(conditions, WORKSHOP_NEED);
        } else if ("아이와".equals(companion)) {
            addNeed(conditions, KIDS_NEED);
        } else if ("연인".equals(companion)) {
            List<String> tags = conditions.getTags() == null ? new ArrayList<>() : new ArrayList<>(conditions.getTags());
            if (!tags.contains(MOOD_TAG)) {
                tags.add(MOOD_TAG);
            }
            conditions.setTags(tags);
        }
    }

    /** 리뷰 확인 요청 하나 더하기 (같은 말이 있거나 3개가 차 있으면 그대로) */
    private void addNeed(PlanConditions conditions, String need) {
        List<String> needs = conditions.getNeeds() == null ? new ArrayList<>() : new ArrayList<>(conditions.getNeeds());
        if (!needs.contains(need) && needs.size() < MAX_NEEDS) {
            needs.add(need);
        }
        conditions.setNeeds(needs);
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
        if (conditions.getAreas() != null && !conditions.getAreas().isEmpty()) {
            planCandidateFinder.addAreas(candidates, conditions, kinds);
            addDongNote(conditions, notes);
        } else if (conditions.getAreaLat() != null) {
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
        if (conditions.getWishVisit() != null) {
            Double nearLat = conditions.getAreaLat() != null ? conditions.getAreaLat() : baseLat; // 같은 이름이 여럿이면 지역 가까운 곳
            Double nearLng = conditions.getAreaLng() != null ? conditions.getAreaLng() : baseLng;
            for (String name : conditions.getWishVisit()) {
                planCandidateFinder.addMustVisit(candidates, name, nearLat, nearLng); // 못 찾으면 일정 다 짠 뒤 안내 (addWishNotes)
            }
        }
        if (!isNearMe && conditions.getAreaLat() == null) {
            planCandidateFinder.addArea(candidates, region); // 지역 좌표 없이 이름만 말했을 때 (대화)
            addRegionKinds(candidates, region, kinds); // 그 지역 중심에서 종류별 후보 + 볼거리 카카오 보충 (11/06)
        }
        return candidates;
    }

    /**
     * 말로만 정한 지역("서울 영등포 여의도")의 낱말마다 카카오로 중심을 찾아 종류별 후보를 더함 (11/06).
     * 이름으로만 찾으면 볼거리(공원·쇼핑몰)가 거의 없어 오후가 비어서, 지도에서 고른 구와 같은 방법을 씀. 못 찾으면 건너뜀
     */
    private void addRegionKinds(Map<String, Place> candidates, String region, List<String> kinds) {
        int found = 0;
        for (String word : region.split("[\\s,·/]+")) {
            if (word.length() < 2 || word.equals("서울") || found >= MAX_REGION_WORDS) {
                continue;
            }
            try {
                List<FoundArea> areas = placeService.getAreaList(word);
                if (!areas.isEmpty()) {
                    FoundArea area = areas.get(0);
                    planCandidateFinder.addKinds(candidates, area.getLat(), area.getLng(), "gu".equals(area.getType()) ? "gu" : "dong", kinds);
                    found++;
                }
            } catch (ApiException e) { // 카카오가 안 되면 이름으로 찾은 후보만 씀
                log.info("지역 중심을 찾지 못함: {}", word);
            }
        }
    }

    /**
     * G 고르기: Gemini가 하루 칸마다 후보 안에서 고르고 이유를 씀. 키가 없거나 실패하면 꼭 갈 곳·고정 일정만 돌려줌
     * (어느 쪽이든 칸 검사·빈 칸 채우기는 PlanRuleMaker.fillSlots가 함)
     */
    private List<AiDraftStop> pickStops(PlanConditions conditions, List<String> messages, Map<String, Place> candidates,
                                        Map<String, List<String>> reasons, int days, PlanGenerateResponse response, String limitRule) {
        if (aiService.isReady()) {
            try {
                AiDraft draft = aiService.askJson(makeDraftPrompt(conditions, messages, candidates, reasons, days, response.getStartDate(), limitRule), AiDraft.class);
                if (draft != null && hasText(draft.getTitle())) {
                    response.setTitle(cut(draft.getTitle(), MAX_TITLE_LENGTH));
                }
                if (draft != null && !draft.getStops().isEmpty()) {
                    return addMissingMust(conditions, draft.getStops(), candidates);
                }
            } catch (ApiException e) {
                log.info("Gemini 일정 고르기 실패 ({}): {}", e.getStatus(), e.getMessage()); // 오류 원문은 서버 기록에만 (11/13)
                response.getNotes().add(makeNote(AI_FAIL_NOTE, null));
                return makeFallbackStops(conditions);
            }
        }
        response.getNotes().add(makeNote(AI_FAIL_NOTE, null));
        return makeFallbackStops(conditions);
    }

    /**
     * 프롬프트: 조건 + 하루 칸 틀(S1 10:00 관광…) + 후보 목록(id | 이름 | 종류 | 분류 | 평점 | 리뷰 근거 | 주소). 좌표는 넣지 않음.
     * Gemini는 칸마다 칸 종류에 맞는 후보 하나와 고른 이유를 JSON으로만 답함
     */
    private String makeDraftPrompt(PlanConditions conditions, List<String> messages, Map<String, Place> candidates,
                                   Map<String, List<String>> reasons, int days, String startDate, String limitRule) {
        String todayRule = "";
        if (startDate.equals(Geo.now().toLocalDate().toString())) {
            // 1일차 칸은 서버가 오늘 남은 시간으로 시각을 당겨 쓰니(PlanRuleMaker.pullToNow), 칸은 다 채우게 함 (11/04)
            todayRule = "오늘 출발하는 여행이야. 1일차는 " + Geo.toHhmm(Geo.nowMinutes() + TODAY_START_AFTER_MIN)
                    + "부터 시작해. 칸 시각이 이미 지났어도 칸은 다 채워 (서버가 시각을 뒤로 당겨 써).\n";
        }
        return "여행 일정을 짜 줘.\n"
             + "조건:\n" + toConditionText(conditions)
             + "사용자가 한 말: " + (messages == null ? "" : String.join(" / ", messages)) + "\n"
             + "기간: " + days + "일 (day는 1부터). 날마다 아래 칸을 채워:\n" + makeSlotText(conditions) + todayRule
             + "규칙:\n"
             + "- 칸마다 후보 목록에서 그 칸 종류에 맞는 곳 하나를 골라 id를 그대로 적어. 같은 곳을 두 번 쓰지 마.\n"
             + "- tags(이런 곳 우선)가 있으면 리뷰 근거에 그 태그가 있는 곳을 먼저 골라.\n"
             + "- needs(장소 요청)가 있으면 리뷰 근거에 그 요청이 적힌 곳을 먼저 골라.\n"
             + "- avoidVisit(피하고 싶은 곳)은 고르지 마.\n"
             + limitRule
             + "- 앞 칸 장소와 주소(구·동)가 가까운 곳으로 이어서 동선이 짧게.\n"
             + "- 꼭 갈 곳과 시간 고정 일정은 slot을 null로, time은 고정 시각(꼭 갈 곳은 알맞은 시각)으로 넣어. 고정 일정의 day는 적힌 일차 그대로. 그 시간과 겹치는 칸은 비워.\n"
             + "- reason은 왜 골랐는지 30자 이내 해요체 한마디. 후보 목록에 있는 정보(종류·평점·리뷰 근거·위치)만 근거로 써.\n"
             + "  '~입니다', '대표적인', '인기 있는', '즐길 수 있는' 같은 뻔한 말 말고 그곳만의 근거를 써 (예: \"리뷰에 단체석 넓다는 말이 많아요\", \"평점 4.8 · 다음 장소까지 걸어서 5분\").\n"
             + "  시간 고정 일정은 reason을 비워 (예약 시각이 칸에 이미 보여서).\n"
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
        pinFixedDays(conditions, picks);
        List<AiDraftStop> result = new ArrayList<>(picks);
        for (AiDraftStop must : makeFallbackStops(conditions)) {
            if (!hasPick(picks, must.getName(), candidates)) {
                result.add(must);
            }
        }
        return result;
    }

    /** Gemini가 고른 칸 중 시간 고정 일정은 사용자가 정한 날·시각으로 다시 맞춤 (Gemini가 날을 틀려도 서버가 지킴, 11/04) */
    private void pinFixedDays(PlanConditions conditions, List<AiDraftStop> picks) {
        if (conditions.getFixed() == null) {
            return;
        }
        for (AiDraftStop pick : picks) {
            for (FixedVisit fixed : conditions.getFixed()) {
                if (planCandidateFinder.isSamePlaceName(pick.getName(), fixed.getName())) { // 11/13: "롯데월드타워" 예약에 "나이키 롯데월드타워점"이 붙던 문제
                    pick.setDay(dayOf(fixed));
                    pick.setTime(fixed.getTime());
                    pick.setFixed(true);
                    pick.setSlot(null);
                    pick.setReason(null); // 고정 일정은 "1일차 저녁 식사 고정 일정입니다" 같은 말 대신 칸의 예약 시각만 (11/07 문구 점검)
                }
            }
        }
    }

    /** 이름이 서로 포함되면 같은 곳 (2자 이상, 띄어쓰기 무시 11/06) */
    private boolean isSameName(String first, String second) {
        return planCandidateFinder.isSameName(first, second);
    }

    /** 고정 일정이 몇째 날인지 (없거나 0 이하면 1일차) */
    private int dayOf(FixedVisit fixed) {
        return fixed.getDay() == null || fixed.getDay() < 1 ? 1 : fixed.getDay();
    }

    /** Gemini 답에 그 이름의 장소가 있는지 (이름이 서로 포함되면 같은 곳) */
    private boolean hasPick(List<AiDraftStop> picks, String name, Map<String, Place> candidates) {
        for (AiDraftStop pick : picks) {
            Place place = pick.getId() == null ? null : candidates.get(pick.getId());
            String pickName = place != null ? place.getName() : pick.getName();
            if (planCandidateFinder.isSamePlaceName(pickName, name)) { // 같은 이름이 든 다른 가게를 고른 걸로 치지 않음 (11/13)
                return true;
            }
        }
        return false;
    }

    /** AI 없이: 꼭 갈 곳을 10시부터 2시간 간격, 고정 일정은 그 시각 */
    private List<AiDraftStop> makeFallbackStops(PlanConditions conditions) {
        List<AiDraftStop> stops = new ArrayList<>();
        int clock = conditions.getStartTime() == null ? FALLBACK_START_MIN : Geo.toMinutes(conditions.getStartTime());
        if (conditions.getFixed() != null) {
            for (FixedVisit fixed : conditions.getFixed()) {
                int stay = isBoardingPlace(fixed.getName()) ? BOARDING_STAY_MIN : FIXED_STAY_MIN;
                AiDraftStop stop = makeDraftStop(fixed.getName(), fixed.getTime(), stay, true);
                stop.setDay(dayOf(fixed));
                stops.add(stop);
            }
        }
        List<AiDraftStop> fixedStops = new ArrayList<>();
        for (AiDraftStop stop : stops) {
            if (stop.getDay() == 1) {
                fixedStops.add(stop); // 꼭 갈 곳은 1일차에 넣어서 1일차 고정 일정과만 겹치는지 봄
            }
        }
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
                                Map<String, List<String>> reasons, int days, Double baseLat, Double baseLng, PlanConditions conditions) {
        Set<String> mustKindsLeft = conditions.getMustKinds() == null ? new HashSet<>() : new HashSet<>(conditions.getMustKinds());
        Place first = candidates.isEmpty() ? null : candidates.values().iterator().next();
        Double nearLat = baseLat != null ? baseLat : first == null ? null : first.getLat();
        Double nearLng = baseLng != null ? baseLng : first == null ? null : first.getLng();
        for (AiDraftStop pick : picked) {
            Place place = pick.getId() == null ? null : candidates.get(pick.getId()); // 후보 밖 id는 믿지 않음
            if (place != null && !isSimilarName(pick.getName(), place.getName()) && !isNamedByUser(place.getName(), conditions)) {
                place = null; // Gemini가 이름과 번호를 섞음 (11/06). 번호 쪽이 사용자가 말한 곳이 아니면 이름으로 다시 찾음
            } else if (place != null && !isSimilarName(pick.getName(), place.getName())) {
                pick.setName(place.getName()); // 번호 쪽이 사용자가 말한 곳이면 그 이름으로 ("63스퀘어" 이름 + 타임스퀘어 번호)
            }
            String userName = findUserName(pick.getName(), conditions);
            if (userName != null && !isBoardingPlace(userName)) {
                Place exact = findSameNamePlace(userName); // 사용자가 말한 곳은 이름이 똑같은 곳으로 ("타임스퀘어"가 그 안 식당 번호로 오던 문제, 11/06)
                place = exact != null ? exact : place;
            }
            if (place == null && Boolean.TRUE.equals(pick.getFixed()) && isBoardingPlace(pick.getName())) {
                place = findBoardingPlace(pick.getName(), nearLat, nearLng); // "서울역"이 "도마 서울역점" 식당으로 잡히지 않게 (11/06)
            }
            if (place == null) {
                place = findSameNamePlace(pick.getName()); // 이름이 똑같은 곳이 있으면 그곳 (11/06)
            }
            if (place == null && Boolean.TRUE.equals(pick.getFixed())) {
                place = planCandidateFinder.findFixedPlace(pick.getName(), nearLat, nearLng); // 예약은 이름이 든 다른 가게로 바꾸지 않음 (11/13)
            }
            if (place == null) {
                place = placeService.getPlaceByName(pick.getName(), nearLat, nearLng);
            }
            if (place == null) {
                String name = pick.getName() == null ? "" : pick.getName();
                response.getNotes().add(makeNote(name + "의 위치를 찾지 못해 뺐어요", placeService.getNaverUrl(name, null)));
                continue;
            }
            Stop stop = toDraftStop(pick, place, days);
            if (place.getUseTime() != null && place.getUseTime().contains(CALL_AHEAD_MARK)) {
                // 관광공사 정보에 "방문 전 연락 필수"인 곳은 미리 알려 줌 (11/07 점검: 삼해소주를 아무 안내 없이 넣었음)
                response.getNotes().add(makeNote(place.getName() + "은(는) 방문 전에 연락이 필요해요", placeService.getNaverUrl(place.getName(), null)));
            }
            stop.setMemo(makeMemo(pick.getReason(), reasons.get(place.getId()), conditions.getCompanion()));
            stop.setImportance(isMustStop(pick, place, conditions, mustKindsLeft) ? MUST : MAYBE); // 여행 중 대안이 꼭은 안 줄임 (11/04)
            response.getStops().add(stop);
        }
    }

    /** 꼭인 칸: 시간 고정 · 꼭 갈 곳 · 꼭 넣을 종류의 첫 칸 (꼭 넣을 종류는 종류마다 한 칸만 꼭으로) */
    private boolean isMustStop(AiDraftStop pick, Place place, PlanConditions conditions, Set<String> mustKindsLeft) {
        if (Boolean.TRUE.equals(pick.getFixed()) || planCandidateFinder.isMustVisit(place, conditions.getMustVisit())) {
            return true;
        }
        return place.getKind() != null && mustKindsLeft.remove(place.getKind()); // Set.remove: 들어 있었으면 빼고 true
    }

    /** 일정 칸 메모 = Gemini가 쓴 이유 + 리뷰 근거 ("성수 감성 카페 · 분위기 좋아요 · 리뷰 10개 중 6개"). 둘 다 없으면 null, 200자까지 */
    private String makeMemo(String reason, List<String> evidence, String companion) {
        List<String> parts = new ArrayList<>();
        if (hasText(reason) && isFitReason(reason, companion)) {
            parts.add(cut(reason.trim(), MAX_REASON_LENGTH));
        }
        if (evidence != null) {
            for (String item : evidence) {
                // 11/12: 친구 5명인데 "데이트하기 좋아요", 같은 근거가 두 번, "리뷰 10개 중 2개"처럼 약한 근거가 붙던 것 정리
                boolean isRepeated = !parts.isEmpty() && parts.get(0).contains(headOf(item));
                if (isFitReason(item, companion) && !isRepeated && !isWeakEvidence(item)) {
                    parts.add(item);
                }
            }
        }
        return parts.isEmpty() ? null : cut(String.join(" · ", parts), MAX_MEMO_LENGTH);
    }

    /** 누구랑에 안 맞는 이유인지: 연인이 아니면 "데이트", 혼자가 아니면 "혼밥" 이유는 안 붙임 */
    private boolean isFitReason(String text, String companion) {
        if (text.contains("데이트") && !"연인".equals(companion)) {
            return false;
        }
        return !text.contains("혼밥") || "혼자".equals(companion);
    }

    /** 근거 앞부분 ("분위기 좋아요 · 리뷰 10개 중 6개" → "분위기") */
    private String headOf(String evidence) {
        String head = evidence.split("[ ·]")[0];
        return head.length() > 2 ? head.substring(0, 2) : head;
    }

    /** "리뷰 10개 중 2개"처럼 리뷰 3개 미만에서 나온 근거는 약해서 안 붙임 */
    private boolean isWeakEvidence(String evidence) {
        java.util.regex.Matcher matcher = WEAK_EVIDENCE.matcher(evidence); // Matcher: 글자에서 패턴을 찾는 자바 도구
        return matcher.find() && Integer.parseInt(matcher.group(1)) < MIN_EVIDENCE_COUNT;
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

    /**
     * 시간 고정 일정 중 타는 곳(이름이 "역"으로 끝나거나 터미널·공항)은 출발 15분 전 도착으로 바꿈 (11/06).
     * "KTX 19:00 서울역"이면 서울역 칸을 18:45로 넣어서, 18:45에 닿으면 정상이고 그보다 늦으면 여행 중에 늦음 알림이 떠요.
     * 요청으로 받은 조건만 바꾸고 저장하지 않아서, 다시 만들어도 두 번 빠지지 않아요.
     */
    private void addBoardingBuffer(PlanConditions conditions, List<PlanNote> notes) {
        if (conditions.getFixed() == null) {
            return;
        }
        for (FixedVisit fixed : conditions.getFixed()) {
            if (!isBoardingPlace(fixed.getName()) || fixed.getTime() == null) {
                continue;
            }
            String departure = fixed.getTime();
            fixed.setTime(Geo.toHhmm(Geo.toMinutes(departure) - BOARDING_BUFFER_MIN));
            notes.add(makeNote(fixed.getName() + "은 " + departure + " 출발 " + BOARDING_BUFFER_MIN + "분 전인 " + fixed.getTime() + " 도착으로 맞췄어요", null));
        }
    }

    /**
     * 꼭 갈 곳 중 Gemini가 안 고른 곳을 마지막 날 시작 시각에 넣음 (칸 없이 → 겹치는 칸은 비우고, 겹치면 뒤로 밀림).
     * "영등포 타임스퀘어"가 가끔 빠지던 문제 (11/06)
     */
    private void addMissingMustPicks(PlanConditions conditions, List<AiDraftStop> picks, int days) {
        if (conditions.getMustVisit() == null) {
            return;
        }
        String lastDayStart = days > 1 ? MIDDLE_DAY_START : (conditions.getStartTime() == null ? Geo.toHhmm(FALLBACK_START_MIN) : conditions.getStartTime());
        for (String name : conditions.getMustVisit()) {
            if (isPicked(name, picks)) {
                continue;
            }
            AiDraftStop stop = makeDraftStop(name, lastDayStart, MUST_STAY_MIN, false);
            stop.setDay(days);
            picks.add(stop);
        }
    }

    /** 이 이름이 Gemini가 고른 곳에 있나 */
    private boolean isPicked(String name, List<AiDraftStop> picks) {
        for (AiDraftStop pick : picks) {
            if (isSimilarName(name, pick.getName())) {
                return true;
            }
        }
        return false;
    }

    /** 같은 장소가 두 번 들어가면 앞(날·시각 순)의 것만 남김. 장소 번호가 없으면 이름으로 */
    private void removeRepeatedStops(List<Stop> stops) {
        stops.sort((first, second) -> first.getDay().equals(second.getDay())
                ? first.getTime().compareTo(second.getTime()) : first.getDay() - second.getDay());
        Set<String> seen = new HashSet<>();
        List<Stop> repeated = new ArrayList<>();
        for (Stop stop : stops) {
            String key = stop.getPlaceId() != null ? stop.getPlaceId() : stop.getName().replace(" ", "");
            if (!seen.add(key)) {
                repeated.add(stop);
            }
        }
        stops.removeAll(repeated);
    }

    /** 마지막 날 타는 곳(역·터미널·공항) 고정 일정 뒤에 오는 칸은 뺌 (KTX를 탄 뒤라 갈 수 없음) */
    private void removeAfterBoarding(List<Stop> stops, int days) {
        Integer boardingAt = null;
        for (Stop stop : stops) {
            if (stop.getDay() == days && Boolean.TRUE.equals(stop.getFixed()) && isBoardingPlace(stop.getName())) {
                boardingAt = Geo.toMinutes(stop.getTime());
                stop.setStay(BOARDING_STAY_MIN); // 타러 가는 것뿐이라 30분 (Gemini가 정한 머묾 대신)
            }
        }
        if (boardingAt == null) {
            return;
        }
        List<Stop> after = new ArrayList<>();
        for (Stop stop : stops) {
            if (stop.getDay() == days && !Boolean.TRUE.equals(stop.getFixed()) && Geo.toMinutes(stop.getTime()) > boardingAt) {
                after.add(stop);
            }
        }
        stops.removeAll(after);
    }

    /** 이 이름과 같은 곳으로 볼 만한 사용자가 말한 곳의 이름 (없으면 null) */
    private String findUserName(String name, PlanConditions conditions) {
        List<String> names = new ArrayList<>();
        if (conditions.getMustVisit() != null) {
            names.addAll(conditions.getMustVisit());
        }
        if (conditions.getFixed() != null) {
            for (FixedVisit fixed : conditions.getFixed()) {
                names.add(fixed.getName());
            }
        }
        for (String userName : names) {
            if (userName != null && isSimilarName(userName, name)) {
                return userName;
            }
        }
        return null;
    }

    /** 사용자가 말한 곳(꼭 갈 곳·가능하면 갈 곳·시간 고정)의 이름인지 */
    private boolean isNamedByUser(String name, PlanConditions conditions) {
        List<String> names = new ArrayList<>();
        if (conditions.getMustVisit() != null) {
            names.addAll(conditions.getMustVisit());
        }
        if (conditions.getWishVisit() != null) {
            names.addAll(conditions.getWishVisit());
        }
        if (conditions.getFixed() != null) {
            for (FixedVisit fixed : conditions.getFixed()) {
                names.add(fixed.getName());
            }
        }
        for (String userName : names) {
            if (isSimilarName(userName, name)) {
                return true;
            }
        }
        return false;
    }

    /** 두 이름이 같은 곳으로 볼 만한지: 띄어쓰기를 빼고 한쪽이 다른 쪽을 품음. 이름이 없으면 그냥 믿음 */
    private boolean isSimilarName(String pickName, String placeName) {
        if (pickName == null || pickName.isBlank() || placeName == null) {
            return true;
        }
        String first = pickName.replace(" ", "");
        String second = placeName.replace(" ", "");
        return first.contains(second) || second.contains(first);
    }

    /**
     * 이름이 똑같은 장소 (없으면 null). 이름으로 찾으면 가까운 곳을 먼저 골라서
     * "영등포 타임스퀘어"가 그 안의 식당 "더플레이스 영등포 타임스퀘어점"으로 바뀌던 문제 (11/06)
     */
    private Place findSameNamePlace(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Place place = placeService.getPlaceByName(name, null, null); // 위치 없이 찾으면 이름이 똑같은 곳이 먼저 옴
        return place != null && name.trim().equals(place.getName()) ? place : null;
    }

    /**
     * 타는 곳(역·터미널·공항) 고정 일정은 카카오 위치 검색에서 분류가 역·터미널·공항인 곳만 고름.
     * 우리 장소 DB는 식당·카페 위주라 이름에 "서울역"이 들어간 가게가 먼저 잡혀서 (11/06). 못 찾으면 null
     */
    private Place findBoardingPlace(String name, Double lat, Double lng) {
        List<com.tripbiseo.common.external.FoundLocation> found; // import 줄을 늘리지 않으려고 전체 이름 (다른 브랜치와 import 충돌 방지)
        try {
            found = placeService.getLocationList(name.replace("KTX", "").replace("SRT", "").trim(), lat, lng);
        } catch (ApiException e) {
            return null; // 카카오 키가 없거나 오류면 원래 방식으로
        }
        for (com.tripbiseo.common.external.FoundLocation location : found) {
            String category = location.getCategory() == null ? "" : location.getCategory();
            if (category.contains("역") || category.contains("터미널") || category.contains("공항") || category.contains("교통")) {
                Place place = new Place();
                place.setName(location.getName());
                place.setCategory(category);
                place.setAddress(location.getAddress());
                place.setLat(location.getLat());
                place.setLng(location.getLng());
                return place;
            }
        }
        return null;
    }

    /** 기차역·터미널·공항인지 (이름으로만 봄. "서울역", "광명역 KTX", "동서울터미널", "김포공항") */
    private boolean isBoardingPlace(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.replace("KTX", "").replace("SRT", "").trim();
        return trimmed.endsWith("역") || trimmed.contains("터미널") || trimmed.contains("공항");
    }

    /** 안내 중에 이 글이 있는지 */
    private boolean hasNote(PlanGenerateResponse response, String text) {
        for (PlanNote note : response.getNotes()) {
            if (text.equals(note.getText())) {
                return true;
            }
        }
        return false;
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
