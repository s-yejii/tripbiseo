package com.tripbiseo.plan.dto;

import lombok.Data;

/** 내 장소 저장 (POST /api/my/places). placeId가 없으면 name으로 우리 DB에서 찾아요 (친구 소식은 이름만 있어서) */
@Data
public class MyPlaceRequest {
    private String placeId;
    private String name;
    private String fromName;    // 누구 소식에서 저장했는지 (예: 민지)
}
