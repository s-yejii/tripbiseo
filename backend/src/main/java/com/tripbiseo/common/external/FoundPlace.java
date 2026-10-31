package com.tripbiseo.common.external;

import lombok.Data;

/** 외부 API(TourAPI·카카오)에서 이름으로 찾은 장소. 우리 DB에 없어서 id가 없고, 약관상 저장하지 않음 */
@Data
public class FoundPlace {
    private String name;
    private String category;
    private String address;
    private double lat;
    private double lng;
}
