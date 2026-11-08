package com.tripbiseo.after.dto;

import lombok.Data;

@Data // 가져올 사진의 정보
public class PhotoResponse {
    private Long id; // 사진의 아이디
    private Long stopId; // 일정별 장소 번호(칸) 이동중이면 null
    private String mediaType; // 영상이냐 사진이냐
    private String url; // api별 파일 링크
    private Integer durationSec; // 영상 재생시간(초)/ 사진이면 null
    private int day;   // 언제 몇째날 찍었는지
    private String takenAt; // 찍은 시각이며 글자형태 HH:mm

}