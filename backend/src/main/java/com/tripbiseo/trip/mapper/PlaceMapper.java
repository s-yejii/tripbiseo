package com.tripbiseo.trip.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.trip.dto.Place;

/**
 * ② 장소 (retrip_place, retrip_review 읽기만). SQL은 resources/mapper/trip/PlaceMapper.xml
 * 반경·가까운 순은 PostGIS(geom 칸 + GIST 인덱스)로 DB에서 계산해요. (CLAUDE.md 거리 계산 규칙)
 */
@Mapper
public interface PlaceMapper {

    /** 장소 하나 (없으면 null) */
    Place selectPlace(String placeId);

    /**
     * 이름 또는 분류 검색 최대 20개. lat·lng가 있으면 가까운 순 + distance, 없으면 리뷰 많은 순.
     * groupPattern: "카페"·"술집"처럼 묶음 말일 때 분류 정규식 (없으면 null).
     * isFood: "음식점"·"식당"·"맛집"이면 true → 음식점(content_type_id 39) 중 카페·술집이 아닌 곳 전부
     */
    List<Place> selectPlaceList(@Param("keyword") String keyword, @Param("kind") String kind,
                                @Param("lat") Double lat, @Param("lng") Double lng,
                                @Param("groupPattern") String groupPattern, @Param("isFood") boolean isFood,
                                @Param("notFoodPattern") String notFoodPattern);

    /** 반경(m) 안 장소, 가까운 순 + distance. category가 있으면 분류에 그 말이 들어간 곳만. 최대 limit개 */
    List<Place> selectNearbyPlaceList(@Param("lat") double lat, @Param("lng") double lng, @Param("radius") int radius,
                                      @Param("category") String category, @Param("limit") int limit);

    /** 주소·이름에 지역 이름이 들어간 장소, 보정 평점 순 최대 60개 */
    List<Place> selectAreaPlaceList(String region);

    /** 반경 안 한 종류(kind) 장소, 보정 평점 순 (③ 일정 만들기 후보) */
    List<Place> selectKindPlaceList(@Param("lat") double lat, @Param("lng") double lng, @Param("radius") int radius,
                                    @Param("kind") String kind, @Param("limit") int limit);

    /** 이름으로 찾기: 같은 이름 → 이름이 들어간 곳, 리뷰 많은 순 최대 5개 */
    List<Place> selectPlaceListByName(String name);

    /** 장소 리뷰 본문, 긴 순 최대 limit개 */
    List<String> selectReviewList(@Param("placeId") String placeId, @Param("limit") int limit);

    /**
     * 인기 장소 (11/03): 다른 회원들의 최근 30일 일정 기록 점수 순 → 같으면 보정 평점 순.
     * 다녀옴 1점·계획 0.3점, 최근 7일 여행은 2배. 평점 3.0 미만·숙박·내가 최근 30일 다녀온 곳은 뺌.
     * lat·lng가 있으면 distance, radius가 있으면 그 반경(m) 안만. 최대 limit개
     */
    List<Place> selectPopularPlaceList(@Param("memberId") long memberId, @Param("lat") Double lat, @Param("lng") Double lng,
                                       @Param("radius") Integer radius, @Param("limit") int limit);
}
