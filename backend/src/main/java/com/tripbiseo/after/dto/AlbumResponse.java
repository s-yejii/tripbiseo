package com.tripbiseo.after.dto;


import java.util.ArrayList;
import java.util.List;

import lombok.Data;

//앨범 화면에 여행 하나가 담기게

@Data
public class AlbumResponse {
    private Long tripId; // 여행번호
    private String title; // 여행제목
    private String startDate; // 여행시작일
    private String endDate; // 여행끝나는날짜
    private int placeCount; // : 장소 개수 + "건너뛴 곳 빼고"
    private int photoCount; // 사진개수계산
    private int videoCount; // 영상계산
    private List<PhotoResponse> photos = new ArrayList<>(); // 사진 영상 목록, 찍은순 최대 8개


}