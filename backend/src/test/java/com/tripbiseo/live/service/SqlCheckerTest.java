package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tripbiseo.live.dto.SqlCheck;

/** ④ AI가 쓴 SQL 검사 (DB 없이) */
class SqlCheckerTest {

    private final SqlChecker sqlChecker = new SqlChecker();

    // ---------------------------------------------------------------- 통과

    @Test
    void prev는_IN_꼴이면_통과하고_쪼개는_SQL로_바꿈() {
        SqlCheck check = sqlChecker.checkSql("SELECT p.id FROM ask_place p WHERE p.id IN (:prev) ORDER BY p.rating_adj DESC LIMIT 10");
        assertTrue(check.isOk(), check.getReason());
        assertTrue(check.getSql().contains("p.id IN (SELECT unnest(string_to_array(#{prev}, ',')))"), check.getSql());
        assertEquals(Set.of("prev"), check.getPlaceholders());
        assertEquals(null, check.getOrderBy());                         // 평점 순은 다시 줄 세우지 않음
    }

    @Test
    void prev를_IN_말고_다른_꼴로_쓰면_탈락() {
        assertFalse(sqlChecker.checkSql("SELECT p.id FROM ask_place p WHERE p.name = :prev").isOk());
        assertFalse(sqlChecker.checkSql("SELECT p.id FROM ask_place p WHERE p.id NOT IN (:prev)").isOk());
    }

    @Test
    void 덜_돌아가는_순이면_orderBy를_적고_LIMIT을_5로_늘림() {
        SqlCheck check = sqlChecker.checkSql(
                "SELECT p.id FROM ask_place p WHERE p.id IN (:prev) ORDER BY detour_minutes(p.geom, :route) LIMIT 1");
        assertTrue(check.isOk(), check.getReason());
        assertEquals("detour", check.getOrderBy());
        assertEquals(1, check.getWantLimit());
        assertTrue(check.getSql().endsWith("LIMIT 5"), check.getSql());
    }

    @Test
    void 가까운_순은_별명으로_정렬해도_near() {
        SqlCheck check = sqlChecker.checkSql(
                "SELECT id, ST_Distance(geom, :me) AS d FROM ask_place WHERE kind = 'cafe' ORDER BY d LIMIT 3");
        assertTrue(check.isOk(), check.getReason());
        assertEquals("near", check.getOrderBy());
        assertEquals(3, check.getWantLimit());
    }

    @Test
    void 가는_길_카페는_통과하고_route를_MyBatis_파라미터로_바꿈() {
        SqlCheck check = sqlChecker.checkSql(
                "SELECT id FROM ask_place WHERE kind = 'cafe' AND detour_minutes(geom, :route) <= 5 ORDER BY rating_adj DESC LIMIT 5");
        assertTrue(check.isOk());
        assertEquals("SELECT id FROM ask_place WHERE kind = 'cafe' AND detour_minutes(geom, ST_GeogFromText(#{route})) <= 5 "
                + "ORDER BY rating_adj DESC LIMIT 5", check.getSql());
        assertEquals(Set.of("route"), check.getPlaceholders());
    }

    @Test
    void 리뷰_검색어를_꺼냄() {
        SqlCheck check = sqlChecker.checkSql("SELECT p.id FROM ask_place p WHERE p.kind = 'cafe' AND ST_DWithin(p.geom, :me, 500) "
                + "AND (SELECT COUNT(*) FROM ask_review r WHERE r.place_id = p.id AND r.content ILIKE '%조용%') >= 2");
        assertTrue(check.isOk());
        assertEquals(List.of("조용"), check.getReviewWords());
        assertTrue(check.getSql().contains("ST_GeogFromText(#{me})"));
    }

    @Test
    void slack은_숫자_파라미터로_바꿈() {
        SqlCheck check = sqlChecker.checkSql("SELECT id FROM ask_place WHERE detour_minutes(geom, :route) + 30 <= :slack LIMIT 5");
        assertTrue(check.isOk());
        assertTrue(check.getSql().contains("<= #{slack}"));
    }

    @Test
    void LIMIT이_없거나_크면_10으로_OFFSET은_지움() {
        assertTrue(sqlChecker.checkSql("SELECT id FROM ask_place").getSql().endsWith("LIMIT 10"));
        assertTrue(sqlChecker.checkSql("SELECT id FROM ask_place LIMIT 50").getSql().endsWith("LIMIT 10"));
        assertEquals("SELECT id FROM ask_place LIMIT 5", sqlChecker.checkSql("SELECT id FROM ask_place LIMIT 5 OFFSET 100000").getSql());
    }

    @Test
    void 끝의_세미콜론_하나는_괜찮음() {
        assertTrue(sqlChecker.checkSql("SELECT id FROM ask_place LIMIT 3;").isOk());
    }

    // ---------------------------------------------------------------- 탈락

    @Test
    void 다른_표는_탈락_하위_쿼리_안이어도() {
        assertFalse(sqlChecker.checkSql("SELECT id FROM member").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM public.ask_place").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE id IN (SELECT member_id::text FROM member)").isOk());
    }

    @Test
    void SELECT가_아니거나_여러_문장이면_탈락() {
        assertFalse(sqlChecker.checkSql("DELETE FROM ask_place").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place; DROP TABLE member").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place UNION SELECT id::text FROM member").isOk());
        assertFalse(sqlChecker.checkSql("WITH m AS (SELECT 1) SELECT id FROM ask_place").isOk());
    }

    @Test
    void 첫_칸이_id가_아니면_탈락() {
        assertFalse(sqlChecker.checkSql("SELECT name FROM ask_place").isOk());
    }

    @Test
    void MyBatis_문법이나_주석이_있으면_탈락() {
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE name = 'a#{b}'").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE name = '${b}'").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place -- 주석").isOk());
    }

    @Test
    void 허용하지_않은_함수나_빈칸은_탈락() {
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE pg_sleep(10) IS NULL").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE name = :password").isOk());
    }

    @Test
    void 상한을_넘는_숫자는_탈락() {
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE detour_minutes(geom, :route) <= 60").isOk());
        assertFalse(sqlChecker.checkSql("SELECT id FROM ask_place WHERE ST_DWithin(geom, :me, 5000)").isOk());
    }

    @Test
    void 문법_오류나_빈_SQL은_탈락() {
        assertFalse(sqlChecker.checkSql("selec id from").isOk());
        assertFalse(sqlChecker.checkSql("").isOk());
        assertFalse(sqlChecker.checkSql(null).isOk());
    }
}
