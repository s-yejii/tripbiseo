package com.tripbiseo.after.dto;

import lombok.Data;

/** 같이 간 사람 하나 (trip_companion 한 줄, 화면에도 이 모양으로 보냄) */
@Data
public class Companion {
    private Long id;             // companion_id
    private Long tripId;
    private String name;         // "나", "동행 1", "지은" … (정산의 낸 사람과 같은 글자)
    private Long friendMemberId; // 친구 기능(①)이 생기면 친구 회원 번호. 지금은 null
    private int sortNo;          // 보이는 순서. 0은 '나'
    private Integer shareAmount; // 더치페이 낼 돈을 직접 정한 금액. null이면 자동 (나머지를 똑같이 나눔)
}
