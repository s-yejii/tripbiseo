package com.tripbiseo.after.service;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import com.tripbiseo.after.dto.AlbumResponse;
import com.tripbiseo.after.dto.PhotoResponse;
import com.tripbiseo.after.dto.PhotoUploadResponse;

/** ⑤ 사진·영상 (앨범, 회고 사진) 서비스 */
public interface PhotoService {

    /** 사진·영상 여러 개 저장. tripId가 null이면 찍은 날짜로 여행을 찾고, 찍은 시각으로 일정 칸을 찾음 */
    PhotoUploadResponse createPhotos(long memberId, Long tripId, List<MultipartFile> files,
                        List<String> takenAts, List<Integer> durations);

    /** 한 여행의 사진·영상 전체, 찍은 순 (남의 여행이면 404) */
    List<PhotoResponse> getPhotoList(long memberId, long tripId);

    /** 앨범: 내 지난 여행마다 사진 앞 8장 + 개수 */
    List<AlbumResponse> getAlbumList(long memberId);

    /** 사진·영상 파일 (내 사진이 아니면 404) */
    Resource getPhotoFile(long memberId, long photoId);
}
