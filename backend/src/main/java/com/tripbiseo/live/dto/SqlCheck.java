package com.tripbiseo.live.dto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.Data;

/** AI가 쓴 SQL을 SqlChecker로 검사한 결과 */
@Data
public class SqlCheck {
    private boolean ok;                                    // 통과했나
    private String sql;                                    // 통과: 빈칸을 MyBatis 파라미터로 바꾼 SQL
    private String reason;                                 // 탈락: 이유 (Gemini에 다시 물을 때 같이 보냄)
    private Set<String> placeholders = new LinkedHashSet<>(); // 쓴 빈칸 (route, me, next, slack, prev)
    private List<String> reviewWords = new ArrayList<>();  // 리뷰에서 찾은 말 (예: 조용). 카드에 근거로 보여 줌
    private String orderBy;                                // 첫 정렬 기준: "detour"(덜 돌아가는 순) / "near"(내 위치에서 가까운 순) / null(그 밖)
    private Integer wantLimit;                             // orderBy가 있을 때 사용자가 원한 개수 (실제 걷는 길로 다시 줄 세운 뒤 이만큼만)
}
