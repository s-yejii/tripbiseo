package com.tripbiseo.trip.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.ExternalApi;
import com.tripbiseo.common.external.PlaceSearchClient;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.dto.PlacePhoto;
import com.tripbiseo.trip.dto.TripPhotoRequest;

import tools.jackson.databind.JsonNode;

/**
 * ② 장소 사진: 사진 파일은 저장하지 않고, 찾은 주소만 서버 메모리에 잠깐 기억해요 (카카오 호출을 줄이려고).
 * 카카오 이미지 검색에는 글 제목이 없어서 가게가 맞는지 알 수 없어, 제목이 있는 블로그 검색을 써요.
 * 제목에 가게 이름이 없으면 엉뚱한 사진일 수 있어서 쓰지 않아요.
 */
@Service
public class PlacePhotoServiceImpl implements PlacePhotoService {

    private static final String BLOG_URL = "https://dapi.kakao.com/v2/search/blog";
    private static final List<String> SIGHT_KINDS = List.of("tour", "culture", "shop");
    private static final String LABEL = "카카오 검색";
    private static final Pattern LIST_TITLE = Pattern.compile("모음|best|베스트|top\\d|\\d+곳|\\d+선|추천\\d");
    private static final Pattern REVIEW_TITLE = Pattern.compile("메뉴|후기|방문");
    private static final int MAX_IDS = 12;       // 한 번에 찾는 장소 수
    private static final int RESULT_SIZE = 10;   // 장소 하나에 살펴볼 글 수
    private static final int CACHE_SIZE = 2000;  // 기억할 장소 수 (넘으면 오래된 것부터 지움)
    private static final String BOLD_TAG = "</?b>"; // 검색어 강조 표시

    private final PlaceService placeService;
    private final ExternalApi externalApi;
    private final PlaceSearchClient placeSearchClient; // TourAPI 공식 사진

    @Value("${KAKAO_REST_API_KEY:}")
    private String kakaoKey;

    // LinkedHashMap의 이 메서드를 덮어쓰면 가득 찼을 때 가장 오래된 칸을 지워요. Optional: 못 찾은 결과도 기억하려고 씀
    private final Map<String, Optional<PlacePhoto>> cache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<PlacePhoto>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    // 장소 id → 후보 사진들 (일정 안에서 겹치면 다음 후보를 쓰려고 여러 장 기억). 못 찾으면 빈 목록
    private final Map<String, List<PlacePhoto>> imageCache = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<PlacePhoto>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    public PlacePhotoServiceImpl(PlaceService placeService, ExternalApi externalApi, PlaceSearchClient placeSearchClient) {
        this.placeService = placeService;
        this.externalApi = externalApi;
        this.placeSearchClient = placeSearchClient;
    }

    /** 장소들의 사진 (찾은 곳만) */
    @Override
    public List<PlacePhoto> getPlacePhotoList(List<String> placeIds) {
        List<PlacePhoto> photos = new ArrayList<>();
        for (String placeId : placeIds.subList(0, Math.min(placeIds.size(), MAX_IDS))) {
            PlacePhoto photo = findPhoto(placeId);
            if (photo != null) {
                photos.add(photo);
            }
        }
        return photos;
    }

    /** 한 일정의 사진: 칸마다 후보에서 아직 안 쓴 첫 사진 */
    @Override
    public List<PlacePhoto> getTripPhotoList(List<TripPhotoRequest> stops) {
        List<PlacePhoto> photos = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (TripPhotoRequest stop : stops.subList(0, Math.min(stops.size(), MAX_IDS))) {
            for (PlacePhoto image : findImages(stop)) {
                if (used.add(image.getImageUrl())) { // add는 처음 넣을 때만 true
                    PlacePhoto photo = makeTripPhoto(image.getImageUrl(), image.getStep(), image.getSiteName(), image.getLinkUrl(), image.getTitle());
                    photo.setPlaceId(stop.getKey());
                    photos.add(photo);
                    break;
                }
            }
        }
        return photos;
    }

    // ---------------------------------------------------------------- 도우미

    private List<PlacePhoto> findImages(TripPhotoRequest stop) {
        String cacheKey = stop.getName() + "/" + guOf(stop.getAddress());
        synchronized (imageCache) {
            if (imageCache.containsKey(cacheKey)) {
                return imageCache.get(cacheKey);
            }
        }
        List<PlacePhoto> images = searchImages(stop);
        synchronized (imageCache) {
            imageCache.put(cacheKey, images);
        }
        return images;
    }

    /** 틀린 사진보다 없는 게 나아요: 1 DB 사진 → 2 관광지는 TourAPI(이름·300m 일치) → 3 음식점·카페는 이름·동네가 맞는 블로그 글 */
    private List<PlacePhoto> searchImages(TripPhotoRequest stop) {
        List<PlacePhoto> images = new ArrayList<>();
        if (stop.getName() == null || stop.getName().isBlank()) {
            return images;
        }
        try {
            Place place = stop.getPlaceId() == null ? null : placeService.getPlace(stop.getPlaceId());
            if (place != null && place.getImageUrl() != null && !place.getImageUrl().isBlank()) {
                images.add(makeTripPhoto(place.getImageUrl().replace("http://", "https://"), 1, null, null, null));
            }
            if (isSightKind(place, stop.getCategory())) {
                addTourImage(stop, images);
                addSightBlogImages(stop, images);
            } else {
                addFoodTourImage(stop, images);
                addBlogImages(stop, images);
            }
        } catch (ApiException e) {
            return images; // 사진이 없으면 화면이 아이콘을 보여 줌
        }
        return images;
    }

    /** 볼거리인지 (관광지·전시·쇼핑). 음식점·카페·술집이 아니면 */
    private boolean isSightKind(Place place, String category) {
        if (place != null && place.getKind() != null) {
            return SIGHT_KINDS.contains(place.getKind());
        }
        return category != null && category.matches(".*(관광지|문화시설|축제|레포츠|쇼핑).*");
    }

    private void addTourImage(TripPhotoRequest stop, List<PlacePhoto> images) {
        if (stop.getLat() == null || stop.getLng() == null) {
            return;
        }
        String image = placeSearchClient.findTourImage(stop.getName(), stop.getLat(), stop.getLng());
        if (image == null) {
            image = placeSearchClient.findNearbyTourImage(PlaceSearchClient.coreName(stop.getName()), stop.getLat(), stop.getLng());
        }
        if (image != null) {
            images.add(makeTripPhoto(image, 2, "TourAPI", null, null));
        }
    }

    private void addFoodTourImage(TripPhotoRequest stop, List<PlacePhoto> images) {
        if (stop.getLat() == null || stop.getLng() == null) {
            return;
        }
        String image = placeSearchClient.findFoodTourImage(stop.getName(), stop.getLat(), stop.getLng());
        if (image != null) {
            images.add(makeTripPhoto(image, 2, "TourAPI", null, null));
        }
    }

    /** 볼거리 블로그 검색: 낱말을 바꿔 가며 찾되, 제목에 핵심어가 있고 요약에 그 구·동 이름이 있는 글만 */
    private void addSightBlogImages(TripPhotoRequest stop, List<PlacePhoto> images) {
        String gu = guOf(stop.getAddress());
        if (kakaoKey.isBlank() || gu.isEmpty()) {
            return;
        }
        String core = PlaceSearchClient.coreName(stop.getName());
        for (String query : List.of(stop.getName(), core + " " + gu, stop.getName() + " 산책")) {
            addMatchedBlogImages(query, core, stop, images, false);
            if (!images.isEmpty()) {
                return;
            }
        }
    }

    /** 블로그 검색: 제목에 가게 이름이 그대로 있고, 요약에 그 구·동 이름이 있는 글의 썸네일만 */
    private void addBlogImages(TripPhotoRequest stop, List<PlacePhoto> images) {
        String gu = guOf(stop.getAddress());
        if (kakaoKey.isBlank() || gu.isEmpty()) {
            return; // 동네를 모르면 같은 가게인지 확인할 수 없어요
        }
        addMatchedBlogImages(stop.getName() + " " + gu, PlaceSearchClient.normalizeName(stop.getName()), stop, images, true);
    }

    /** 블로그 검색 한 번: 제목에 name이 있고 요약에 구·동 이름이 있는 글의 썸네일을 담음 */
    private void addMatchedBlogImages(String query, String name, TripPhotoRequest stop, List<PlacePhoto> images, boolean isStore) {
        String gu = guOf(stop.getAddress());
        String dong = dongOf(stop.getAddress());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("size", RESULT_SIZE);
        JsonNode json = externalApi.getJson(externalApi.buildUrl(BLOG_URL, params), Map.of("Authorization", "KakaoAK " + kakaoKey), LABEL);
        for (JsonNode doc : json.path("documents")) {
            String title = doc.path("title").asString("").replaceAll(BOLD_TAG, "");
            String contents = doc.path("contents").asString("").replaceAll(BOLD_TAG, "");
            String thumbnail = doc.path("thumbnail").asString("");
            boolean isSameArea = contents.contains(gu) || (!dong.isEmpty() && contents.contains(dong));
            if (isGoodTitle(title, name, isStore) && isSameArea && !thumbnail.isBlank()) {
                images.add(makeTripPhoto(thumbnail.replace("http://", "https://"), 3, doc.path("blogname").asString(""), doc.path("url").asString(""), title));
            }
        }
    }

    /**
     * 그곳만 다룬 글인지 제목으로 봐요: 모음·BEST·"n곳" 같은 글은 뺌.
     * 가게는 제목이 가게 이름으로 시작하거나, 이름과 함께 메뉴·후기·방문이 있어야 해요 (여러 곳 중 하나로 나온 글의 사진이 엉뚱해서)
     */
    private boolean isGoodTitle(String title, String name, boolean isStore) {
        String text = compact(title);
        if (!text.contains(name) || LIST_TITLE.matcher(text).find()) {
            return false;
        }
        return !isStore || text.replaceAll("^[\\[(<【]+", "").startsWith(name) || REVIEW_TITLE.matcher(text).find();
    }

    private PlacePhoto makeTripPhoto(String imageUrl, int step, String siteName, String linkUrl, String title) {
        PlacePhoto photo = makePhoto(null, imageUrl, siteName, linkUrl);
        photo.setStep(step);
        photo.setTitle(title);
        return photo;
    }

    /** 주소에서 구 이름 ("서울 강서구 ..." → 강서구). 모르면 빈 글 */
    private String guOf(String address) {
        String[] words = address == null ? new String[0] : address.split(" ");
        return words.length > 1 ? words[1] : "";
    }

    /** 주소에서 동 이름 ("서울 강서구 화곡동 ..." → 화곡동). 도로명 주소면 빈 글 */
    private String dongOf(String address) {
        String[] words = address == null ? new String[0] : address.split(" ");
        return words.length > 2 && words[2].endsWith("동") ? words[2] : "";
    }

    private PlacePhoto findPhoto(String placeId) {
        synchronized (cache) {
            if (cache.containsKey(placeId)) {
                return cache.get(placeId).orElse(null);
            }
        }
        PlacePhoto photo = searchPhoto(placeId);
        synchronized (cache) {
            cache.put(placeId, Optional.ofNullable(photo));
        }
        return photo;
    }

    private PlacePhoto searchPhoto(String placeId) {
        try {
            Place place = placeService.getPlace(placeId);
            if (place.getImageUrl() != null && !place.getImageUrl().isBlank()) {
                return makePhoto(placeId, place.getImageUrl().replace("http://", "https://"), null, null);
            }
            return searchBlog(place);
        } catch (ApiException e) {
            return null; // 사진은 없어도 화면은 기본 사진으로 보여 줌
        }
    }

    /** "{가게 이름} {구}"로 블로그 검색 → 제목에 가게 이름이 있고 사진이 있는 첫 글 */
    private PlacePhoto searchBlog(Place place) {
        if (kakaoKey.isBlank()) {
            return null;
        }
        String[] words = place.getAddress() == null ? new String[0] : place.getAddress().split(" ");
        String query = place.getName() + (words.length > 1 ? " " + words[1] : "");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("size", RESULT_SIZE);
        JsonNode json = externalApi.getJson(externalApi.buildUrl(BLOG_URL, params), Map.of("Authorization", "KakaoAK " + kakaoKey), LABEL);
        String name = compact(place.getName());
        for (JsonNode doc : json.path("documents")) {
            String title = compact(doc.path("title").asString("").replaceAll(BOLD_TAG, ""));
            String thumbnail = doc.path("thumbnail").asString("");
            if (title.contains(name) && !thumbnail.isBlank()) {
                return makePhoto(place.getId(), thumbnail, doc.path("blogname").asString(""), doc.path("url").asString(""));
            }
        }
        return null;
    }

    private PlacePhoto makePhoto(String placeId, String imageUrl, String siteName, String linkUrl) {
        PlacePhoto photo = new PlacePhoto();
        photo.setPlaceId(placeId);
        photo.setImageUrl(imageUrl);
        photo.setSiteName(siteName);
        photo.setLinkUrl(linkUrl);
        return photo;
    }

    private String compact(String text) {
        return text.replaceAll("\\s+", "").toLowerCase();
    }
}
