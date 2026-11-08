package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tripbiseo.common.ai.AiService;

/** ④ 여행 중 채팅 말 분류 (낱말 규칙만, Gemini 꺼 둠 → 애매하면 질문) */
class LiveChatClassifierTest {

    private LiveChatClassifier classifier;

    @BeforeEach
    void setUp() {
        AiService aiService = mock(AiService.class);
        when(aiService.isReady()).thenReturn(false);
        classifier = new LiveChatClassifier(aiService);
    }

    @Test
    void 종류와_추천_말이_있으면_다음이_섞여도_추천() {
        assertEquals("recommend", classifier.classify("다음 장소 근처 카페").getKind());
        assertEquals("recommend", classifier.classify("다음 일정 전에 다녀올 디저트집").getKind());
        assertEquals("recommend", classifier.classify("가는 길에 5분 안 카페").getKind());
        assertEquals("recommend", classifier.classify("여유 있을 때 들를 술집").getKind());
    }

    @Test
    void 종류_없이_다음만_있으면_질문() {
        assertEquals("question", classifier.classify("다음 일정 몇 시야?").getKind());
    }

    @Test
    void 바꾸기_말이_있으면_추천으로_보지_않음() {
        assertEquals("change", classifier.classify("늦을 것 같아 일정 바꿔줘").getKind());
        assertEquals("question", classifier.classify("근처 카페 빼줘").getKind()); // 추천+바꾸기 → 애매 → (Gemini 없음) 질문
    }

    @Test
    void 예전_추천_말은_그대로() {
        assertEquals("recommend", classifier.classify("근처 카페 추천해줘").getKind());
        assertEquals("recommend", classifier.classify("배고픈데 먹을 데").getKind());
    }
}
