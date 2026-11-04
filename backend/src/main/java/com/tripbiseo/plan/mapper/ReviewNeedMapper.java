package com.tripbiseo.plan.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tripbiseo.plan.dto.ReviewSnippet;

/**
 * ③ 칩에 없는 요청을 리뷰로 확인할 때 리뷰 꺼내기 (11/04). retrip_review는 공통 테이블이라 읽기(SELECT)만 해요.
 * SQL은 resources/mapper/plan/ReviewNeedMapper.xml
 */
@Mapper
public interface ReviewNeedMapper {

    /** 후보 장소들의 리뷰 중 단어(patterns: "%단체석%")가 하나라도 들어간 것만, 장소마다 최근 perPlace개까지 */
    List<ReviewSnippet> selectReviewSnippetList(@Param("placeIds") List<String> placeIds, @Param("patterns") List<String> patterns,
                                                @Param("perPlace") int perPlace);
}
