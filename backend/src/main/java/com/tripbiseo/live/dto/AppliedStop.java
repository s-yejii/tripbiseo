package com.tripbiseo.live.dto;

import lombok.Data;

/** 대안 적용 후 오늘 칸 하나 (skipped 제외) */
@Data
public class AppliedStop {
    private Long id;
    private String time;
    private String name;
    private String category;
    private Integer stay;
    private String tag;   // done(다녀옴·지금 있는 곳) / kept / changed(머무는 시간이 바뀜) / added
    private String note;  // "60분 → 30분" 등, null 가능
}
