package com.tripbiseo.common.external;

import lombok.Data;

/** 카카오로 찾은 지역 하나 (어디로 가요?). 약관상 저장하지 않고 고른 이름·좌표만 여행 조건에 씀 */
@Data
public class FoundArea {
    private String name;      // 화면에 보일 이름 (예: 성수동1가, 성동구, 홍대입구역)
    private String fullName;  // 전체 이름 (예: 서울 성동구 성수동1가)
    private String type;      // gu(구) / dong(동) / spot(동네·역, 키워드로 찾은 것)
    private double lat;       // 중심 좌표
    private double lng;
}
