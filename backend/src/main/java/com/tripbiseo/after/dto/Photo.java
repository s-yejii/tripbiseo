package com.tripbiseo.after.dto;

import lombok.Data;

// 휴대폰에서 올라온 사진을 db에 저장하거나 꺼낼때도 위한 dto
@Data 
public class Photo {
    private Long photoId; // 사진의 아이디
    private Long tripId; // 여행 번호
    private Long stopId; // 일정상에 장소 번호 이동중이면 NULL
    private String mediaType; // 영상이냐 사진이냐
    private String fileName; // 서버가 새로 지은 난수 이름
    private String originalName; // 휴대폰에서 올린 사진의 원래 이름
    private Integer durationSec; // 영상 재생시간(초)/ 사진이면 null
    private String takenAt; // 찍은 시각이며 글자형태 연·월·일 + T + 시:분 

}
