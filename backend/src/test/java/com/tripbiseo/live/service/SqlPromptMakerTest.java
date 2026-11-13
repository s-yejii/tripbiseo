package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.tripbiseo.live.dto.SqlCheck;

/** ④ AI에게 묻기 프롬프트 (Gemini 없이) */
class SqlPromptMakerTest {

    private final SqlPromptMaker sqlPromptMaker = new SqlPromptMaker();
    private final SqlChecker sqlChecker = new SqlChecker();

    @Test
    void 이어지는_질문_예시도_모두_검사를_통과함() {
        for (String[] example : SqlPromptMaker.FOLLOW_EXAMPLES) {
            SqlCheck check = sqlChecker.checkSql(example[1]);
            assertTrue(check.isOk(), example[0] + " → " + check.getReason());
            assertTrue(check.getPlaceholders().contains("prev"), example[0]);
        }
    }

    @Test
    void 앞_결과가_있으면_이어지는_질문_규칙과_앞_질문이_들어감() {
        String prompt = sqlPromptMaker.makePrompt("이 중에 조용한 데", "가는 길에 카페", Set.of("route", "me", "prev"), null);
        assertTrue(prompt.contains("[이어지는 질문]"));
        assertTrue(prompt.contains("앞 질문: \"가는 길에 카페\""));
    }

    @Test
    void 예시_SQL은_모두_검사를_통과함() {
        // 예시가 검사에서 떨어지면 Gemini도 따라 쓰다가 떨어져요
        for (String[] example : SqlPromptMaker.EXAMPLES) {
            if (example[1] == null) {
                continue;
            }
            SqlCheck check = sqlChecker.checkSql(example[1]);
            assertTrue(check.isOk(), example[0] + " → " + check.getReason());
        }
    }

    @Test
    void 사용자_말과_쓸_수_있는_빈칸이_들어감() {
        Set<String> usable = new LinkedHashSet<>(List.of("me", "next"));
        String prompt = sqlPromptMaker.makePrompt("근처 \"조용한\" 카페", null, usable, null);
        assertTrue(prompt.contains("사용자 말: \"근처 '조용한' 카페\""));
        assertTrue(prompt.contains("지금 쓸 수 있는 빈칸: :me, :next"));
        assertFalse(prompt.contains("다시 써"));
    }

    @Test
    void 다시_물을_때는_떨어진_이유가_들어감() {
        String prompt = sqlPromptMaker.makePrompt("카페", null, Set.of("me"), "첫 번째 칸은 id여야 함");
        assertTrue(prompt.contains("방금 쓴 SQL은 쓸 수 없어: \"첫 번째 칸은 id여야 함\""));
    }

    @Test
    void 좌표_숫자는_안_들어감() {
        String prompt = sqlPromptMaker.makePrompt("가는 길 카페", null, Set.of("route", "me"), null);
        assertFalse(prompt.matches("(?s).*\\d{2,3}\\.\\d{4,}.*")); // 37.5435 같은 숫자 없음
    }
}
