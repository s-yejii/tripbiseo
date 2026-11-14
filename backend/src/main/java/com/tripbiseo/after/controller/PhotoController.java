package com.tripbiseo.after.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tripbiseo.after.dto.AlbumResponse;
import com.tripbiseo.after.dto.PhotoResponse;
import com.tripbiseo.after.dto.PhotoUploadResponse;
import com.tripbiseo.after.dto.ShortsResponse;
import com.tripbiseo.after.service.PhotoService;
import com.tripbiseo.after.service.ShortsService;
import com.tripbiseo.common.auth.LoginUser;

import lombok.RequiredArgsConstructor;

/** ⑤ 사진·영상 (앨범, 회고 사진) */
@RestController
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;
    private final ShortsService shortsService;
    private final LoginUser loginUser;   // 지금 로그인한 회원 (loginUser.id() = 회원 번호)

    /** 앨범: 내 지난 여행마다 사진 앞 8장 + 개수 */
    @GetMapping("/api/albums")
    public List<AlbumResponse> getAlbumList() {
        return photoService.getAlbumList(loginUser.id());
    }

    /** 한 여행의 사진·영상 전체 (찍은 순) */
    @GetMapping("/api/trips/{id}/photos")
    public List<PhotoResponse> getPhotoList(@PathVariable long id) {
        return photoService.getPhotoList(loginUser.id(), id);
    }

    /** 추억 쇼츠: 사진마다 AI 감성 자막 + 여행 분위기 (S19-d) */
    @GetMapping("/api/trips/{id}/shorts")
    public ShortsResponse getShorts(@PathVariable long id) {
        return shortsService.getShorts(loginUser.id(), id);
    }

    /** 사진·영상 여러 개 올리기. tripId가 없으면 찍은 날짜로 여행을 찾음 */
    @PostMapping("/api/photos")
    public PhotoUploadResponse createPhotos(@RequestParam(value = "files", required = false) List<MultipartFile> files,
                                            @RequestParam(value = "takenAts", required = false) List<String> takenAts,
                                            @RequestParam(value = "durations", required = false) List<Integer> durations,
                                            @RequestParam(value = "tripId", required = false) Long tripId) {
        return photoService.createPhotos(loginUser.id(), tripId, files, takenAts, durations);
    }

    /** 사진·영상 파일 (<img>·<video> 주소) */
    @GetMapping("/api/photos/{photoId}/file")
    public ResponseEntity<Resource> getPhotoFile(@PathVariable long photoId) {
        Resource file = photoService.getPhotoFile(loginUser.id(), photoId);
        // 파일 확장자로 종류 알아내기 (.jpg → image/jpeg, .mp4 → video/mp4). 모르면 그냥 "파일"
        // orElse: 찾으면 그 값, 못 찾으면 괄호 안 값 (Optional이라는 상자에서 꺼내는 방법)
        MediaType type = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(file);
    }
}
