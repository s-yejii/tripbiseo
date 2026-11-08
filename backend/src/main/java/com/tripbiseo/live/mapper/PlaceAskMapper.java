package com.tripbiseo.live.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.live.dto.AskedPlace;

/**
 * ④ AI에게 묻기: AI가 쓴 SQL(SqlChecker 통과) 실행. SQL은 resources/mapper/live/PlaceAskMapper.xml
 * 부르는 곳은 AskSqlRunner 하나 (트랜잭션 안에서 묻기 전용 계정으로 바꾼 뒤 실행)
 */
@Mapper
public interface PlaceAskMapper {

    /** 이 트랜잭션 동안만 묻기 전용 계정으로 (ask_place·ask_review 읽기만) */
    void setRoleAskReader();

    /** 이 트랜잭션 동안만 쿼리 하나 3초 제한 */
    void setStatementTimeout();

    /** AI가 쓴 SQL 실행 → 장소 id (SQL이 정한 순서). 빈칸 값이 없으면 null로 들어감 */
    List<String> selectPlaceIdsBySql(@Param("sql") String sql, @Param("route") String route,
                                     @Param("me") String me, @Param("next") String next,
                                     @Param("slack") Integer slack);

    /** 고른 장소에 정보 붙이기 (+분·점선 시작점은 route가 있을 때만) */
    List<AskedPlace> selectAskedPlaceList(@Param("ids") List<String> ids, @Param("route") String route);

    /** 지도용 경로 띠 (경로 양옆 meters m) → GeoJSON 글자 */
    String selectRouteBand(@Param("route") String route, @Param("meters") int meters);
}
