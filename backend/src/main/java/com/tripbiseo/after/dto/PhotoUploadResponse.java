package com.tripbiseo.after.dto;
import lombok.Data;

// 사진 올리기 결과

@Data 
public class PhotoUploadResponse {
    private int savedCount; // 사진 몇개 저장되는 수 계산
    private int unmatchedCount; // 매칭이 안된 사진, 날짜가 안맞는 여행을 찾지 못해 저장 못한 사진의 수를 적어줌
    
}
