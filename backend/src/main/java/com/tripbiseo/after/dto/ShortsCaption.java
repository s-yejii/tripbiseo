package com.tripbiseo.after.dto;

import lombok.Data;

// 쇼츠 사진 한 장의 자막
@Data
public class ShortsCaption {
    private Long photoId;   // 사진 번호 (photo.photo_id)
    private String caption; // 화면에 보여 줄 자막
}
