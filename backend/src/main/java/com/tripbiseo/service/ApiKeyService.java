package com.tripbiseo.service;

import static com.tripbiseo.common.ApiException.fail;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.mapper.ApiKeyMapper;

/**
 * 외부 API 키 한곳 관리.
 *  읽는 순서: ① api_key 테이블(개발용 키 관리 화면에서 저장) ② application.properties / 환경변수
 *  화면에서 저장하면 서버 재시작 없이 바로 적용. 키 원문은 화면에 앞뒤 4글자만 보여 줍니다.
 */
@Service
public class ApiKeyService {

    /** 키 이름, 화면 이름, 도움말 */
    public record Field(String name, String label, String help) {}

    public static final List<Field> FIELDS = List.of(
            new Field("GEMINI_API_KEY", "Gemini API 키", "Google AI Studio에서 발급. 일정 만들기·재분석·추천 문장 이해"),
            new Field("GEMINI_MODEL", "Gemini 모델", "비우면 gemini-3.8-flash"),
            new Field("KAKAO_JS_KEY", "카카오 JavaScript 키", "지도 표시. 카카오 콘솔 → 플랫폼 Web에 http://localhost:5173 등록"),
            new Field("KAKAO_REST_API_KEY", "카카오 REST API 키", "DB에 없는 장소의 좌표 찾기"),
            new Field("TMAP_APP_KEY", "TMAP 앱 키", "대중교통·도보·자동차 길찾기 (대중교통은 상품 신청 필요)"),
            new Field("ODSAY_API_KEY", "ODsay 키", "TMAP 길찾기가 실패하면 대신 대중교통 길찾기 (서버 IP 등록)"),
            new Field("TOUR_API_KEY", "TourAPI 키", "공공데이터포털 '한국관광공사_국문 관광정보 서비스_GW' (Decoding 키). DB에 없는 장소 찾기. 비우면 기상청 키를 씀"),
            new Field("KMA_SERVICE_KEY", "기상청 단기예보 키", "공공데이터포털 '기상청_단기예보 조회서비스' (Decoding 키). 제미나이가 날씨 도구로 부름"));

    private final ApiKeyMapper mapper;
    private final Environment env;
    private final Map<String, String> saved = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public ApiKeyService(ApiKeyMapper mapper, Environment env) {
        this.mapper = mapper;
        this.env = env;
    }

    /** 쓸 수 있는 키 값. 없으면 null */
    public String get(String name) {
        load();
        String v = saved.get(name);
        if (v == null || v.isBlank()) v = env.getProperty(name);
        return v == null || v.isBlank() ? null : v.trim();
    }

    public boolean has(String name) {
        return get(name) != null;
    }

    /** 키 관리 화면: 있는지 + 가린 값 */
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Field f : FIELDS) {
            String v = get(f.name());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", f.name());
            row.put("label", f.label());
            row.put("help", f.help());
            row.put("set", v != null);
            row.put("masked", v == null ? null : f.name().equals("GEMINI_MODEL") ? v : mask(v));
            out.add(row);
        }
        return out;
    }

    /** {이름: 값}. 빈 값은 삭제(설정 파일 값으로 돌아감) */
    @Transactional
    public List<Map<String, Object>> save(Map<String, String> keys) {
        if (keys == null || keys.isEmpty()) throw fail(400, "저장할 키가 없어요.");
        for (Map.Entry<String, String> e : keys.entrySet()) {
            if (FIELDS.stream().noneMatch(f -> f.name().equals(e.getKey()))) throw fail(400, "모르는 키 이름이에요: " + e.getKey());
            String v = e.getValue() == null ? "" : e.getValue().trim();
            if (v.length() > 400 || v.contains(" ")) throw fail(400, e.getKey() + " 값 형식을 확인해 주세요. (띄어쓰기 없이 400자 이하)");
            if (v.isEmpty()) mapper.delete(e.getKey());
            else mapper.save(e.getKey(), v);
        }
        loaded = false;
        return list();
    }

    private void load() {
        if (loaded) return;
        synchronized (this) {
            if (loaded) return;
            saved.clear();
            for (Map<String, Object> row : mapper.findAll()) saved.put(String.valueOf(row.get("name")), String.valueOf(row.get("value")));
            loaded = true;
        }
    }

    private static String mask(String v) {
        return v.length() <= 8 ? "••••" : v.substring(0, 4) + "••••" + v.substring(v.length() - 4);
    }
}
