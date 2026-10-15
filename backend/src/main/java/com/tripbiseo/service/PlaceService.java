package com.tripbiseo.service;

import static com.tripbiseo.common.ApiException.fail;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.ApiException;
import com.tripbiseo.common.Geo;
import com.tripbiseo.common.Http;
import com.tripbiseo.common.Json;
import com.tripbiseo.common.Maps;
import com.tripbiseo.dto.Place;
import com.tripbiseo.mapper.PlaceMapper;

/**
 * 장소: 우리 DB(retrip_place 장소 11,733곳 · retrip_review 리뷰 11만 건)에서 찾습니다.
 * DB에 없는 장소는 TourAPI(전국 관광정보) → 카카오 로컬 순으로 좌표만 찾고, 그래도 없으면 네이버 지도 검색 링크를 줍니다.
 */
@Service
public class PlaceService {

    private static final String[] WEEK = {"월", "화", "수", "목", "금", "토", "일"};
    private static final Pattern RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*[-~]\\s*(\\d{1,2}:\\d{2})");
    private static final Pattern FOOD = Pattern.compile("식당|맛집|음식|밥|한식|중식|일식|양식|고기|분식");
    private static final Map<String, String> TOUR_TYPES = Map.of("12", "관광지", "14", "문화시설", "15", "축제·행사", "28", "레포츠", "32", "숙박", "38", "쇼핑", "39", "음식점");

    private final PlaceMapper mapper;
    private final ApiKeyService keys;

    public PlaceService(PlaceMapper mapper, ApiKeyService keys) {
        this.mapper = mapper;
        this.keys = keys;
    }

    // ---------------------------------------------------------------- 조회
    public List<Map<String, Object>> search(String keyword, Double lat, Double lng) {
        if (keyword == null || keyword.isBlank() || keyword.length() > 40) throw fail(400, "검색어를 1~40자로 입력해 주세요.");
        return mapper.search(keyword.trim(), kindOf(keyword), lat, lng).stream().map(p -> card(p, lat, lng)).toList();
    }

    /** 지도에서 누른 곳 근처 (거리순 10곳) */
    public List<Map<String, Object>> nearby(double lat, double lng, int radius) {
        return within(lat, lng, Math.min(Math.max(radius, 50), 2000), null).stream()
                .sorted(Comparator.comparing(p -> p.distance)).limit(10).map(p -> card(p, null, null)).toList();
    }

    /**
     * 반경 안 추천 3곳: 리뷰 수로 보정한 평점 순.
     * 도착 예상 시각(걸어서 직선거리×1.3)에 문을 닫았거나 오늘 휴무인 곳은 빼고, 마감 시각·남은 분·마감 임박 여부를 같이 돌려줌
     */
    public Map<String, Object> recommend(double lat, double lng, int radius, String category, int stay) {
        boolean food = category != null && FOOD.matcher(category).find();
        LocalDate today = Geo.now().toLocalDate();
        int now = Geo.nowMin();
        int skipped = 0;
        List<Map<String, Object>> out = new ArrayList<>();
        List<Place> list = within(lat, lng, radius, food ? null : category).stream()
                .filter(p -> !food || isFood(p.category))
                .filter(p -> p.rating != null)
                .sorted(Comparator.comparing((Place p) -> adjusted(p.rating, p.reviewCount)).reversed()).toList();
        for (Place p : list) {
            int arrive = now + (int) Math.ceil(p.distance * 1.3 / 75); // 걸어서 가는 시간
            Map<String, Object> info = openInfo(p, today, arrive, stay);
            if (Boolean.FALSE.equals(info.get("open"))) {
                skipped++;
                continue;
            }
            Map<String, Object> m = card(p, null, null);
            m.putAll(info);
            out.add(m);
            if (out.size() == 3) break;
        }
        return Maps.of("places", out, "closedSkipped", skipped);
    }

    public Map<String, Object> detail(String id) {
        Place p = find(id);
        LocalDate today = Geo.now().toLocalDate();
        Map<String, Object> m = card(p, null, null);
        m.put("tel", p.tel);
        m.put("imageUrl", p.imageUrl == null ? null : p.imageUrl.replaceFirst("^http://", "https://"));
        m.put("useTime", p.useTime);
        m.put("restDate", p.restDate);
        m.put("hours", hours(p, today));
        m.put("openNow", closedOn(p, today) != null ? Boolean.FALSE : openAt(p, today, Geo.nowMin()));
        m.put("amenities", p.amenities == null ? List.of() : Arrays.stream(p.amenities.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
        m.put("reviews", mapper.reviews(p.id, 10));
        return m;
    }

    public Place find(String id) {
        Place p = id == null ? null : mapper.findById(id);
        if (p == null) throw fail(404, "장소를 찾을 수 없어요.");
        return p;
    }

    public List<Place> inArea(String keyword) {
        return keyword == null || keyword.isBlank() ? List.of() : mapper.inArea(keyword.trim());
    }

    public List<Place> within(double lat, double lng, int radius, String category) {
        double[] b = Geo.box(lat, lng, radius);
        List<Place> out = new ArrayList<>();
        for (Place p : mapper.inBox(b[0], b[1], b[2], b[3], category)) {
            p.distance = Geo.meters(lat, lng, p.lat, p.lng);
            if (p.distance <= radius) out.add(p);
        }
        return out;
    }

    /**
     * 이름으로 장소 찾기 (제미나이가 말한 장소를 실제 위치로 바꿀 때).
     * ① 우리 DB 이름 → ② '분식집'처럼 종류로 말했으면 근처(2km) 그 분류에서 보정 평점 1위 → ③ TourAPI → ④ 카카오 → 못 찾으면 null
     */
    public Place resolve(String name, Double nearLat, Double nearLng) {
        if (name == null || name.isBlank()) return null;
        List<Place> found = mapper.findByName(name.trim());
        if (!found.isEmpty()) {
            if (nearLat == null) return found.get(0);
            return found.stream().min(Comparator.comparing(p -> Geo.meters(nearLat, nearLng, p.lat, p.lng))).get();
        }
        if (nearLat != null) {
            Place best = within(nearLat, nearLng, 2000, kindOf(name)).stream().filter(p -> p.rating != null)
                    .max(Comparator.comparing(p -> adjusted(p.rating, p.reviewCount))).orElse(null);
            if (best != null) return best;
        }
        Place p = tourSearch(name, nearLat, nearLng);
        return p != null ? p : kakaoSearch(name, nearLat, nearLng);
    }

    /** "분식집" → "분식", "카페" → "카페" (분류 검색용) */
    static String kindOf(String word) {
        String k = word.trim().replaceAll("(맛집|집|가게|전문점)$", "");
        return k.isEmpty() ? word.trim() : k;
    }

    /** TourAPI 키워드 검색 (전국). 키는 공공데이터포털 키라 기상청 키와 같은 값이어도 됨 */
    private Place tourSearch(String name, Double lat, Double lng) {
        String key = keys.has("TOUR_API_KEY") ? keys.get("TOUR_API_KEY") : keys.get("KMA_SERVICE_KEY");
        if (key == null) return null;
        if (key.contains("%")) key = URLDecoder.decode(key, StandardCharsets.UTF_8);
        try {
            Object json = Http.getJson(Http.url("https://apis.data.go.kr/B551011/KorService2/searchKeyword2", Http.params("serviceKey", key,
                    "MobileOS", "ETC", "MobileApp", "tripbiseo", "_type", "json", "numOfRows", 10, "pageNo", 1, "keyword", name)), null, 6000, "TourAPI");
            List<Place> found = new ArrayList<>();
            for (Object it : Json.list(json, "response", "body", "items", "item")) {
                Place p = new Place();
                p.name = Json.str(it, "title");
                p.category = TOUR_TYPES.getOrDefault(Json.str(it, "contenttypeid"), "관광지");
                p.address = Json.str(it, "addr1");
                p.lat = Json.num(it, "mapy");
                p.lng = Json.num(it, "mapx");
                if (p.name != null && p.lat != null && p.lng != null) found.add(p);
            }
            // 이름이 같은 곳 → 이름이 짧은 곳("N서울타워"가 "한쿡 N서울타워"보다 먼저) → 가까운 곳
            return found.stream().min(Comparator.comparing((Place p) -> p.name.equals(name) ? 0 : 1).thenComparing(p -> p.name.length())
                    .thenComparing(p -> lat == null ? 0 : Geo.meters(lat, lng, p.lat, p.lng))).orElse(null); // 저장하지 않음. id 없음
        } catch (ApiException e) {
            return null;
        }
    }

    private Place kakaoSearch(String name, Double lat, Double lng) {
        String key = keys.get("KAKAO_REST_API_KEY");
        if (key == null) return null;
        try {
            Map<String, Object> q = Http.params("query", name, "size", 1);
            if (lat != null) q.putAll(Http.params("x", lng, "y", lat, "sort", "accuracy"));
            Object doc = Json.at(Http.getJson(Http.url("https://dapi.kakao.com/v2/local/search/keyword.json", q),
                    Map.of("Authorization", "KakaoAK " + key), 6000, "카카오 로컬"), "documents", 0);
            if (doc == null) return null;
            Place p = new Place();
            p.name = Json.str(doc, "place_name");
            p.category = last(Json.str(doc, "category_name"));
            p.address = Json.str(doc, "road_address_name") != null && !Json.str(doc, "road_address_name").isBlank() ? Json.str(doc, "road_address_name") : Json.str(doc, "address_name");
            p.lat = Json.num(doc, "y");
            p.lng = Json.num(doc, "x");
            return p; // 카카오 결과는 저장하지 않음(약관). id 없음
        } catch (ApiException e) {
            return null;
        }
    }

    /** DB에 없는 장소용: 네이버 지도 검색 링크 (구글 검색 대신) */
    public static String naverMapLink(String name, String address) {
        String area = address == null ? "" : Arrays.stream(address.split("\\s+")).filter(w -> w.matches(".*(구|동)$")).findFirst().orElse("");
        return "https://map.naver.com/p/search/" + Http.encPath((name + " " + area).trim());
    }

    // ---------------------------------------------------------------- 영업시간
    /** 요일별 영업시간 목록 [{day, text, today}] */
    public static List<Map<String, Object>> hours(Place p, LocalDate today) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (p.useTime == null) return out;
        String todayName = WEEK[today.getDayOfWeek().getValue() - 1];
        int i = p.useTime.indexOf("요일별:");
        if (i < 0) {
            out.add(Maps.of("day", "매일", "text", p.useTime, "today", true));
            return out;
        }
        // "금(10/9) 11:30-22:00"처럼 날짜가 붙은 표기는 '/'로 자르면 깨지므로 날짜 괄호를 먼저 지움
        String body = p.useTime.substring(i + 4).replaceAll("\\(\\s*\\d{1,2}\\s*/\\s*\\d{1,2}\\s*\\)", "").replace(")", "");
        for (String part : body.split("/")) {
            String t = part.trim();
            if (t.length() < 2) continue;
            String day = t.startsWith("매일") ? "매일" : t.substring(0, 1);
            out.add(Maps.of("day", day, "text", t.substring(day.length()).trim(), "today", day.equals(todayName) || day.equals("매일")));
        }
        return out;
    }

    /** 그 날 영업시간 [여는 분, 닫는 분(새벽까지면 1440 넘음)]. 24시간이면 [0, 2880], 그날 휴무면 [0, 0], 정보가 없으면 null */
    static int[] range(Place p, LocalDate date) {
        if (p.useTime == null) return null;
        if (p.useTime.contains("상시") || p.useTime.contains("24시간")) return new int[] {0, 2880};
        String text = p.useTime;
        String dayName = WEEK[date.getDayOfWeek().getValue() - 1];
        for (Map<String, Object> h : hours(p, date))
            if (dayName.equals(h.get("day")) || "매일".equals(h.get("day"))) text = String.valueOf(h.get("text"));
        if (text.contains("휴무")) return new int[] {0, 0};
        Matcher m = RANGE.matcher(text);
        if (!m.find()) return null;
        int open = Geo.min(m.group(1)), close = Geo.min(m.group(2));
        if (close <= open) close += 1440; // 새벽까지 영업
        return new int[] {open, close};
    }

    /** 그 날 그 시각(분)에 열려 있는지. 정보가 없으면 null */
    public static Boolean openAt(Place p, LocalDate date, int minute) {
        int[] r = range(p, date);
        if (r == null) return null;
        return (minute >= r[0] && minute < r[1]) || (minute + 1440 >= r[0] && minute + 1440 < r[1]);
    }

    /**
     * 도착 시각 기준 영업 정보: open(열려 있나, 모르면 null), closeAt("22:00"), minutesLeft(도착 후 닫을 때까지 분),
     * closingSoon(머무는 시간 또는 60분 안에 닫음), closedReason(휴무·영업 끝 이유 글)
     */
    public static Map<String, Object> openInfo(Place p, LocalDate date, int arrive, int stay) {
        Map<String, Object> m = new LinkedHashMap<>();
        String rest = p == null ? null : closedOn(p, date);
        if (rest != null) {
            m.put("open", false);
            m.put("closedReason", "오늘 휴무 (" + rest + ")");
            return m;
        }
        int[] r = p == null ? null : range(p, date);
        if (r == null) {
            m.put("open", null);
            return m;
        }
        Boolean open = openAt(p, date, arrive);
        m.put("open", open);
        if (r[1] - r[0] >= 1440) return m; // 24시간
        m.put("closeAt", Geo.hhmm(r[1]));
        if (Boolean.FALSE.equals(open)) {
            m.put("closedReason", arrive < r[0] ? Geo.hhmm(r[0]) + "에 문을 열어요" : Geo.hhmm(r[1]) + "에 영업이 끝나요");
            return m;
        }
        int left = (arrive >= r[0] ? r[1] : r[1] - 1440) - arrive;
        m.put("minutesLeft", left);
        m.put("closingSoon", left <= Math.max(60, stay));
        return m;
    }

    /** 휴무일이면 이유 글, 아니면 null */
    public static String closedOn(Place p, LocalDate date) {
        String dayName = WEEK[date.getDayOfWeek().getValue() - 1];
        if (p.restDate != null && p.restDate.contains(dayName + "요일") && !p.restDate.contains("째")) return p.restDate;
        if (p.useTime != null && p.useTime.matches(".*(요일별:|/)\\s*" + dayName + "\\s*(정기)?휴무.*")) return dayName + "요일 휴무";
        return null;
    }

    // ---------------------------------------------------------------- 변환
    /** 화면용 장소 카드 */
    public static Map<String, Object> card(Place p, Double lat, Double lng) {
        Map<String, Object> m = Maps.of("id", p.id, "name", p.name, "category", p.category, "address", p.address, "lat", p.lat, "lng", p.lng,
                "rating", p.rating, "reviewCount", p.reviewCount, "naverUrl", p.mapUrl != null ? p.mapUrl : naverMapLink(p.name, p.address));
        Double d = p.distance != null ? p.distance : lat != null && lng != null ? Geo.meters(lat, lng, p.lat, p.lng) : null;
        if (d != null) m.put("distance", Math.round(d));
        return m;
    }

    /** 리뷰가 적은 5.0보다 리뷰가 많은 4.6을 더 믿게: (리뷰수×평점 + 100×4.6) / (리뷰수+100) */
    public static double adjusted(Double rating, Integer count) {
        int n = count == null ? 0 : count;
        return (n * (rating == null ? 0 : rating) + 100 * 4.6) / (n + 100);
    }

    public static boolean isIndoor(String category) {
        return category != null && !category.matches(".*(공원|산|한강|거리|해변|숲|길|광장|시장|레포츠|축제).*");
    }

    private static boolean isFood(String c) {
        return c != null && !c.matches(".*(카페|디저트|베이커리|숙박|관광|문화|공원|쇼핑).*");
    }

    private static String last(String category) {
        return category == null ? null : category.substring(category.lastIndexOf('>') + 1).trim();
    }
}
