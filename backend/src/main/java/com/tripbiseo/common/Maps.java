package com.tripbiseo.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** null도 넣을 수 있는 순서 있는 Map: Maps.of("a", 1, "b", null) */
public final class Maps {
    private Maps() {}

    public static Map<String, Object> of(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(String.valueOf(kv[i]), kv[i + 1]);
        return m;
    }
}
