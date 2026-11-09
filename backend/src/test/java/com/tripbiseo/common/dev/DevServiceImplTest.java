package com.tripbiseo.common.dev;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// 시연 계정 목록 판정 (11/09: 설정이 비어 있어도 qwer1234는 시연 계정)
class DevServiceImplTest {

    @Test
    void 설정이_없으면_qwer1234() {
        assertTrue(DevServiceImpl.isInAccounts(null, "qwer1234"));
    }

    @Test
    void 설정이_빈_값이어도_qwer1234() {
        assertTrue(DevServiceImpl.isInAccounts("", "qwer1234"));
        assertTrue(DevServiceImpl.isInAccounts("   ", "qwer1234"));
    }

    @Test
    void 빈_값이면_다른_계정은_아님() {
        assertFalse(DevServiceImpl.isInAccounts("", "tripuser"));
    }

    @Test
    void 쉼표_목록에_있으면_시연_계정() {
        assertTrue(DevServiceImpl.isInAccounts("qwer1234, tripuser", "tripuser"));
        assertFalse(DevServiceImpl.isInAccounts("qwer1234,tripuser", "demo_001"));
    }
}
