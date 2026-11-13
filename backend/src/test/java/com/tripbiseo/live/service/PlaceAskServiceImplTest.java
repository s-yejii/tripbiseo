package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.external.Route;
import com.tripbiseo.common.external.RouteClient;
import com.tripbiseo.live.dto.AreaInfo;
import com.tripbiseo.live.dto.AskSituation;
import com.tripbiseo.live.dto.AskedPlace;
import com.tripbiseo.live.dto.NextRoute;
import com.tripbiseo.live.dto.PlaceAskResult;
import com.tripbiseo.live.dto.SqlAnswer;
import com.tripbiseo.live.dto.StatusResponse;
import com.tripbiseo.trip.dto.OpenInfo;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.PlaceService;

/**
 * ④ AI에게 묻기 흐름 (Gemini·DB·길찾기는 가짜로)
 * 상황: 여의나루역 → 더현대(다음 칸, 약 400m) 걸어가는 중, 15:00 고정 일정까지 여유 40분
 */
class PlaceAskServiceImplTest {

    private static final long MEMBER_ID = 1L;
    private static final long TRIP_ID = 10L;
    private static final double LAT = 37.5271;
    private static final double LNG = 126.9326;
    private static final double A_LAT = 37.5265; // 카페 a
    private static final double B_LAT = 37.5263; // 카페 b
    private static final String ROUTE_CAFE = "SELECT id FROM ask_place WHERE kind = 'cafe' AND detour_minutes(geom, :route) <= 5 LIMIT 5";
    private static final String PREV_TOP_RATED = "SELECT p.id FROM ask_place p WHERE p.id IN (:prev) ORDER BY p.rating_adj DESC LIMIT 10";
    private static final String PREV_LEAST_DETOUR = "SELECT p.id FROM ask_place p WHERE p.id IN (:prev) ORDER BY detour_minutes(p.geom, :route) LIMIT 1";
    private static final String NEXT_CAFE = "SELECT id FROM ask_place WHERE kind = 'cafe' AND ST_DWithin(geom, :next, 500) LIMIT 5";

    private RouteClient routeClient;
    private AiService aiService;
    private AskSqlRunner askSqlRunner;
    private PlaceService placeService;
    private PlaceAskServiceImpl placeAskService;

    @BeforeEach
    void setUp() {
        LiveTripReader liveTripReader = mock(LiveTripReader.class);
        StatusService statusService = mock(StatusService.class);
        routeClient = mock(RouteClient.class);
        aiService = mock(AiService.class);
        askSqlRunner = mock(AskSqlRunner.class);
        placeService = mock(PlaceService.class);
        placeAskService = new PlaceAskServiceImpl(liveTripReader, statusService, routeClient, aiService,
                new SqlPromptMaker(), new SqlChecker(), askSqlRunner, placeService, new WalkRanker(routeClient));

        when(liveTripReader.getOngoingTrip(anyLong(), anyLong(), any(), any())).thenReturn(makeTrip());
        when(statusService.getStatus(anyLong(), anyLong(), any())).thenReturn(makeStatus());
        when(routeClient.chooseMode(any(), anyDouble(), any())).thenReturn("walk");
        when(aiService.isReady()).thenReturn(true);
        when(askSqlRunner.getRouteBand(anyString(), anyInt())).thenReturn("{band}");
        OpenInfo open = new OpenInfo();
        open.setOpen(true);
        open.setCloseAt("21:30");
        when(placeService.getOpenInfo(any(), any(), anyInt(), anyInt())).thenReturn(open);
    }

    @Test
    void 가는_길_카페를_찾고_닫힌_곳은_빼고_여유를_계산함() {
        answerWith(sqlAnswer(ROUTE_CAFE));
        when(askSqlRunner.run(any(), any())).thenReturn(List.of(place("a", 4), place("b", 5)));
        OpenInfo closed = new OpenInfo();
        closed.setOpen(false);
        OpenInfo open = new OpenInfo();
        open.setOpen(true);
        open.setCloseAt("21:30");
        when(placeService.getOpenInfo(any(), any(), anyInt(), anyInt())).thenReturn(open, closed);

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "가는 길에 5분 안 카페", null, null, LAT, LNG);

        assertFalse(result.isFallback());
        assertEquals(1, result.getPlaces().size());              // b는 닫혀서 빠짐
        assertEquals("21:30", result.getPlaces().get(0).getCloseAt());
        assertEquals(6, result.getPlaces().get(0).getSlackAfter()); // 40 − (4 + 30)
        assertEquals("{band}", result.getBand());
        assertEquals(ROUTE_CAFE, result.getSql());                // 화면에는 빈칸 그대로 (좌표 없음)
        assertTrue(runSituation().getRoute().startsWith("SRID=4326;LINESTRING(126.9326 37.5271"));
    }

    @Test
    void 표에_없는_조건이면_이유만_안내하고_실행하지_않음() {
        SqlAnswer answer = new SqlAnswer();
        answer.setSupported(false);
        answer.setReason("가격 정보가 없어서 가격으로는 찾을 수 없어요");
        answerWith(answer);

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "만원 이하 파스타", null, null, LAT, LNG);

        assertFalse(result.isFallback());
        assertEquals("가격 정보가 없어서 가격으로는 찾을 수 없어요", result.getMessage());
        verify(askSqlRunner, never()).run(any(), any());
    }

    @Test
    void 검사에서_두_번_떨어지면_fallback() {
        answerWith(sqlAnswer("SELECT name FROM ask_place"), sqlAnswer("SELECT id FROM member"));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "카페", null, null, LAT, LNG);

        assertTrue(result.isFallback());
        List<String> prompts = askedPrompts(2);
        assertTrue(prompts.get(1).contains("방금 쓴 SQL은 쓸 수 없어: \"첫 번째 칸은 id여야 함\""));
        verify(askSqlRunner, never()).run(any(), any());
    }

    @Test
    void 대중교통_구간이면_가는_길은_빼고_물어봄() {
        when(routeClient.chooseMode(any(), anyDouble(), any())).thenReturn("transit");
        answerWith(sqlAnswer(NEXT_CAFE));
        when(askSqlRunner.run(any(), any())).thenReturn(List.of(place("a", null)));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "다음 장소 근처 카페", null, null, LAT, LNG);

        assertFalse(result.isFallback());
        assertTrue(askedPrompts(1).get(0).contains("지금 쓸 수 있는 빈칸: :me, :next, :slack"));
        assertNull(runSituation().getRoute());
        assertNull(result.getBand());
        assertNull(result.getPlaces().get(0).getSlackAfter());    // 돌아가는 분을 몰라서 계산 안 함
    }

    @Test
    void 지금_없는_빈칸을_쓰면_다시_물어봄() {
        when(routeClient.chooseMode(any(), anyDouble(), any())).thenReturn("transit");
        answerWith(sqlAnswer(ROUTE_CAFE), sqlAnswer(NEXT_CAFE));
        when(askSqlRunner.run(any(), any())).thenReturn(List.of(place("a", null)));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "가는 길에 카페", null, null, LAT, LNG);

        assertFalse(result.isFallback());
        assertTrue(askedPrompts(2).get(1).contains("지금 쓸 수 없는 빈칸 :route"));
        assertEquals(NEXT_CAFE, result.getSql());
    }

    @Test
    void 이어지는_질문은_앞_결과_안에서만_찾음() {
        answerWith(sqlAnswer(PREV_TOP_RATED));
        when(askSqlRunner.run(any(), any())).thenReturn(List.of(place("b", 3)));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "그중에 평점 높은 순으로",
                "가는 길에 카페", List.of("a", "b", " "), LAT, LNG);

        assertFalse(result.isFallback());
        assertEquals("a,b", runSituation().getPrev());                  // 빈 칸은 빼고 쉼표로
        String prompt = askedPrompts(1).get(0);
        assertTrue(prompt.contains("앞 질문: \"가는 길에 카페\""));
        assertTrue(prompt.contains("p.id IN (:prev)"));
    }

    @Test
    void 이어지는_질문인데_prev를_빼먹으면_다시_물어봄() {
        answerWith(sqlAnswer(ROUTE_CAFE), sqlAnswer(PREV_TOP_RATED));
        when(askSqlRunner.run(any(), any())).thenReturn(List.of(place("a", 3)));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "이 중에 평점 높은 데",
                "가는 길에 카페", List.of("a", "b"), LAT, LNG);

        assertFalse(result.isFallback());
        assertTrue(askedPrompts(2).get(1).contains("WHERE에 p.id IN (:prev)를 꼭 넣어"));
        assertEquals(PREV_TOP_RATED, result.getSql());
    }

    @Test
    void 앞_결과가_없으면_prev를_쓸_수_없음() {
        answerWith(sqlAnswer(ROUTE_CAFE));
        when(askSqlRunner.run(any(), any())).thenReturn(List.of(place("a", 3)));

        placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "가는 길에 카페", null, null, LAT, LNG);

        assertNull(runSituation().getPrev());
        assertFalse(askedPrompts(1).get(0).contains("[이어지는 질문]"));
    }

    @Test
    void 덜_돌아가는_순이면_실제_걷는_길로_다시_줄_세우고_원한_개수만() {
        answerWith(sqlAnswer(PREV_LEAST_DETOUR));
        // 직선으로는 a가 1등(1분)이지만 실제 걸음으로는 b가 덜 돌아감
        when(askSqlRunner.run(any(), any())).thenReturn(new ArrayList<>(List.of(place("a", 1), place("b", 2))));
        when(routeClient.getRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq("walk"), any(), eq(true)))
                .thenAnswer(call -> walkRoute(call.getArgument(0), call.getArgument(2)));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "이 중에 제일 안 돌아가는 데",
                "가는 길에 카페", List.of("a", "b"), LAT, LNG);

        assertEquals(1, result.getPlaces().size());                    // LIMIT 1 → 5곳 뽑아 다시 잰 뒤 1곳만
        assertEquals("b", result.getPlaces().get(0).getId());
        assertEquals(2, result.getPlaces().get(0).getDetourMinutes()); // (4 + 4) − 6
        assertEquals(8, result.getPlaces().get(0).getSlackAfter());    // 40 − (2 + 30)
        assertFalse(result.getPlaces().get(0).getIsEstimate());
    }

    @Test
    void DB가_3초를_넘기면_fallback() {
        answerWith(sqlAnswer(ROUTE_CAFE));
        when(askSqlRunner.run(any(), any())).thenThrow(new QueryTimeoutException("statement timeout"));

        PlaceAskResult result = placeAskService.findPlaces(MEMBER_ID, TRIP_ID, "가는 길에 5분 안 카페", null, null, LAT, LNG);

        assertTrue(result.isFallback());
    }

    // ---------------------------------------------------------------- 도우미

    /** Gemini가 차례로 이 답들을 줌 */
    private void answerWith(SqlAnswer first, SqlAnswer... rest) {
        when(aiService.askJsonQuick(anyString(), eq(SqlAnswer.class))).thenReturn(first, rest);
    }

    /** Gemini에 보낸 프롬프트들 (count번 물어봤는지도 확인) */
    private List<String> askedPrompts(int count) {
        ArgumentCaptor<String> prompts = ArgumentCaptor.forClass(String.class);
        verify(aiService, times(count)).askJsonQuick(prompts.capture(), eq(SqlAnswer.class));
        return prompts.getAllValues();
    }

    /** AskSqlRunner.run에 넘긴 빈칸 값 */
    private AskSituation runSituation() {
        ArgumentCaptor<AskSituation> situation = ArgumentCaptor.forClass(AskSituation.class);
        verify(askSqlRunner).run(any(), situation.capture());
        return situation.getValue();
    }

    private SqlAnswer sqlAnswer(String sql) {
        SqlAnswer answer = new SqlAnswer();
        answer.setSupported(true);
        answer.setSql(sql);
        answer.setExplain("설명");
        return answer;
    }

    /**
     * 가짜 걷기 길찾기: 출발 위도로 구간을 구분
     * 나(LAT) → 더현대 6분 / 나 → a 1분 / a → 더현대 9분 / 나 → b 4분 / b → 더현대 4분
     */
    private Route walkRoute(double fromLat, double toLat) {
        Route route = new Route();
        route.setSource("tmap");
        if (fromLat == LAT) {
            route.setMinutes(toLat == 37.5259 ? 6 : toLat == A_LAT ? 1 : 4);
        } else {
            route.setMinutes(fromLat == A_LAT ? 9 : 4);
        }
        return route;
    }

    private AskedPlace place(String id, Integer detourMinutes) {
        AskedPlace place = new AskedPlace();
        place.setId(id);
        place.setName("카페 " + id);
        place.setDetourMinutes(detourMinutes);
        place.setLat("a".equals(id) ? A_LAT : B_LAT);
        place.setLng(126.93);
        return place;
    }

    private Trip makeTrip() {
        Stop next = new Stop();
        next.setId(101L);
        next.setDay(1);
        next.setName("더현대 서울");
        next.setLat(37.5259);
        next.setLng(126.9284);
        next.setState("todo");
        Trip trip = new Trip();
        trip.setId(TRIP_ID);
        trip.setTransports(new ArrayList<>(List.of("walk", "subway")));
        trip.getStops().add(next);
        return trip;
    }

    private StatusResponse makeStatus() {
        NextRoute next = new NextRoute();
        next.setId(101L);
        next.setCoords(new ArrayList<>(List.of(new double[] {LAT, LNG}, new double[] {37.5259, 126.9284})));
        AreaInfo area = new AreaInfo();
        area.setSlackMin(40);
        StatusResponse status = new StatusResponse();
        status.setNext(next);
        status.setArea(area);
        return status;
    }
}
