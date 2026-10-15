package com.tripbiseo.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.tripbiseo.dto.Place;

/** 장소·리뷰 (retrip_place, retrip_review). SQL은 resources/mapper/PlaceMapper.xml */
@Mapper
public interface PlaceMapper {

    @Select("SELECT COUNT(*) FROM place")
    int countPlace(); // 팀 DB 연결 확인용(/db-test)

    List<Place> search(@Param("keyword") String keyword, @Param("kind") String kind, @Param("lat") Double lat, @Param("lng") Double lng);
    List<Place> inBox(@Param("minLat") double minLat, @Param("maxLat") double maxLat, @Param("minLng") double minLng,
                      @Param("maxLng") double maxLng, @Param("category") String category);
    Place findById(String id);
    List<Place> findByName(@Param("name") String name);
    List<Place> inArea(@Param("keyword") String keyword);
    List<String> reviews(@Param("placeId") String placeId, @Param("limit") int limit);
}
