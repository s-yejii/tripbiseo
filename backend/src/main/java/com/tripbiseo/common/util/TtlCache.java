package com.tripbiseo.common.util;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 같은 외부 API 요청을 잠깐 기억해 호출 수(=무료 한도·비용)를 아낍니다. 서버 메모리에만 두고 DB에는 저장하지 않습니다
 * (카카오 로컬 API는 결과 저장이 약관상 허용되지 않음).
 */
public class TtlCache {

    private record Entry(Object value, long until) {}

    private final Map<String, Entry> map = new ConcurrentHashMap<>();
    private final int max;

    public TtlCache(int max) {
        this.max = max;
    }

    /**
     * key로 기억한 값이 ttlMs 안이면 그대로, 아니면 loader를 실행해 새로 받아 기억합니다.
     * <T>: 돌려줄 값의 타입을 부르는 쪽이 정함(Route, Forecast …). Supplier: "값을 만드는 방법"을 넘기는 람다 () -> …
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, long ttlMs, Supplier<T> loader) {
        Entry hit = map.get(key);
        if (hit != null && hit.until() > System.currentTimeMillis()) return (T) hit.value();
        T value = loader.get(); // 실패(예외)는 기억하지 않음
        if (map.size() >= max) {
            Iterator<String> it = map.keySet().iterator();
            if (it.hasNext()) map.remove(it.next());
        }
        map.put(key, new Entry(value, System.currentTimeMillis() + ttlMs));
        return value;
    }
}
