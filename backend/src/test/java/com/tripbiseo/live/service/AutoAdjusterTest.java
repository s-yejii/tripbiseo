package com.tripbiseo.live.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// 자동 조정 기준 여유 (11/09: 시연 계정만 넉넉하게)
class AutoAdjusterTest {

    @Test
    void 일반_계정은_20분() {
        assertEquals(20, AutoAdjuster.targetSlackFor(false));
    }

    @Test
    void 시연_계정은_45분() {
        assertEquals(45, AutoAdjuster.targetSlackFor(true));
    }
}
