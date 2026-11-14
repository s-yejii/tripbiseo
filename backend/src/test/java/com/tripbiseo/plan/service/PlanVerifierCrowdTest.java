package com.tripbiseo.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// 검증 줄의 붐빔 세 단계 (11/13 주형 A안): 서울 순위 80 이상 붐빔, 50~79 보통, 50 미만 한산
class PlanVerifierCrowdTest {

    // 붐빔 말 만들기는 DB·다른 서비스를 안 써서 null로 만들어도 돼요
    private final PlanVerifier verifier = new PlanVerifier(null, null);

    @Test
    void 순위_92면_붐빔_상위_8퍼센트() {
        assertEquals("붐빔(서울 상위 8%)", verifier.crowdLevelText(92));
    }

    @Test
    void 순위_80이면_붐빔() {
        assertEquals("붐빔(서울 상위 20%)", verifier.crowdLevelText(80));
    }

    @Test
    void 순위_57이면_보통() {
        assertEquals("보통", verifier.crowdLevelText(57));
    }

    @Test
    void 순위_35면_한산() {
        assertEquals("한산", verifier.crowdLevelText(35));
    }

    @Test
    void 순위_100이어도_상위_1퍼센트() {
        assertEquals("붐빔(서울 상위 1%)", verifier.crowdLevelText(100));
    }
}
