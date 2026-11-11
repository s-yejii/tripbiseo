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
        String prompt = sqlPromptMaker.makePrompt("근처 \"조용한\" 카페", usable, null);
        assertTrue(prompt.contains("사용자 말: \"근처 '조용한' 카페\""));
        assertTrue(prompt.contains("지금 쓸 수 있는 빈칸: :me, :next"));
        assertFalse(prompt.contains("다시 써"));
    }

    @Test
    void 단어장과_새_표가_들어감() { // 11/11: 특징 키워드·메뉴
        String prompt = sqlPromptMaker.makePrompt("조용한 카페", Set.of("me"), null);
        assertTrue(prompt.contains("[단어장]"));
        assertTrue(prompt.contains("조용한·차분한 → 차분한 분위기예요, 대화하기 좋아요"));
        assertTrue(prompt.contains("ask_menu: 메뉴"));
        assertTrue(prompt.contains("keywords("));
        assertFalse(prompt.contains("가격·메뉴·혼잡도·웨이팅 시간은 표에 없어")); // 예전 규칙이 남아 있으면 안 됨
    }

    @Test
    void 다시_물을_때는_떨어진_이유가_들어감() {
        String prompt = sqlPromptMaker.makePrompt("카페", Set.of("me"), "첫 번째 칸은 id여야 함");
        assertTrue(prompt.contains("방금 쓴 SQL은 쓸 수 없어: \"첫 번째 칸은 id여야 함\""));
    }

    @Test
    void 좌표_숫자는_안_들어감() {
        String prompt = sqlPromptMaker.makePrompt("가는 길 카페", Set.of("route", "me"), null);
        assertFalse(prompt.matches("(?s).*\\d{2,3}\\.\\d{4,}.*")); // 37.5435 같은 숫자 없음
    }
}
