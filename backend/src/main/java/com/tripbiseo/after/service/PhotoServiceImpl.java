package com.tripbiseo.after.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.tripbiseo.after.dto.AlbumResponse;
import com.tripbiseo.after.dto.Photo;
import com.tripbiseo.after.dto.PhotoResponse;
import com.tripbiseo.after.dto.PhotoUploadResponse;
import com.tripbiseo.after.mapper.PhotoMapper;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private static final int ALBUM_PHOTO_COUNT = 8;               // 앨범에 여행마다 보여 줄 사진 수 (2줄 × 4칸)
    private static final long MAX_FILE_BYTES = 100L * 1024 * 1024;   // 파일 하나 100MB
    private static final DateTimeFormatter TAKEN_AT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final PhotoMapper  photoMapper;
    private final TripService  tripService;

    // 사진을 저장할 폴더. application.properties의 photo.dir (환경변수 PHOTO_DIR, 기본 uploads/photos)
    @Value("${photo.dir}")
    private String photoDir;

    /** 사진·영상 여러 개 저장. 하나라도 DB 저장에 실패하면 전부 되돌림 */
    @Override
    @Transactional
    public PhotoUploadResponse createPhotos(long memberId, Long tripId, List<MultipartFile> files,
                                            List<String> takenAts, List<Integer> durations) {
        checkFiles(files, takenAts, durations);

        // tripId가 있으면 그 여행 하나(남의 여행이면 여기서 404), 없으면 내 여행 전부에서 날짜로 찾음
        Trip chosenTrip = null;
        if (tripId != null) {
            chosenTrip = tripService.getTrip(memberId, tripId);
        }
        List<Trip> myTrips = tripService.getTripList(memberId);

        PhotoUploadResponse response = new PhotoUploadResponse();
        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);
            String takenAt = readTakenAt(file, takenAts.get(i));

            Trip trip = chosenTrip;
            if (trip == null) {
                trip = findTripByDate(memberId, myTrips, takenAt.substring(0, 10));
            }
            if (trip == null) {
                response.setUnmatchedCount(response.getUnmatchedCount() + 1); // 맞는 여행이 없음 → 저장 안 함
                continue;
            }

            photoMapper.insertPhoto(toPhoto(file, trip, takenAt, durations.get(i)));
            response.setSavedCount(response.getSavedCount() + 1);
        }
        return response;
    }

    // DB에 넣을 한 줄 만들기 (파일은 여기서 폴더에 저장)
    private Photo toPhoto(MultipartFile file, Trip trip, String takenAt, Integer duration) {
        String mediaType = toMediaType(file);
        Photo photo = new Photo();
        photo.setTripId(trip.getId());
        photo.setStopId(findStopId(trip, takenAt));
        photo.setMediaType(mediaType);
        photo.setFileName(saveFile(file));
        photo.setOriginalName(file.getOriginalFilename());
        photo.setTakenAt(takenAt);
        if (mediaType.equals("video") && duration != null && duration > 0) {
            photo.setDurationSec(duration); // 사진이거나 길이를 모르면 null로 둠
        }
        return photo;
    }
    /**
     * 찍은 날짜(yyyy-MM-dd)가 기간(startDate ~ endDate) 안에 드는 내 여행을 찾아,
     * 일정 칸까지 들어 있는 여행으로 돌려줌. 없으면 null
     */
    private Trip findTripByDate(long memberId, List<Trip> myTrips, String takenDate) {
        for (Trip trip : myTrips) {
            LocalDate photoday = LocalDate.parse(takenDate);
            LocalDate startDate = LocalDate.parse(trip.getStartDate());
            LocalDate endDate = LocalDate.parse(trip.getEndDate());
            // 시작일 ≤ 찍은 날 ≤ 끝날 (같은 날도 포함)
            if (!photoday.isBefore(startDate) && !photoday.isAfter(endDate)) {
                return tripService.getTrip(memberId, trip.getId()); // 목록의 여행은 일정 칸이 비어 있어서 다시 불러옴
            }
        }
        return null; // 다 봤는데 맞는 여행이 없음
    }

    /** 찍은 시각(yyyy-MM-ddTHH:mm)에 머물고 있던 일정 칸 번호. 이동 중이면 null */
    private Long findStopId(Trip trip, String takenAt) {
        // 1) 몇째 날인지: 여행 시작 날짜 ~ 찍은 날짜 차이 + 1
        LocalDate start = LocalDate.parse(trip.getStartDate());
        LocalDate taken = LocalDate.parse(takenAt.substring(0, 10));
        int day = (int) java.time.temporal.ChronoUnit.DAYS.between(start, taken) + 1; // 두 날짜 사이 일 수

        // 2) 찍은 시각을 분으로: "10:20" → 620
        int takenMinutes = toMinutes(takenAt.substring(11, 16));

        // 3) 그날 칸을 하나씩 보면서 [시작, 끝) 안이면 그 칸 번호
        for (Stop stop : trip.getStops()) {
            if (!stop.getDay().equals(day)) continue;          // 다른 날 칸이면 건너뛰기
            if ("skipped".equals(stop.getState())) continue;   // 안 간 곳엔 사진이 없음

            // 시작: 우선 계획 시각, 실제 도착이 있으면 그걸로 덮어쓰기
            int startMinutes = toMinutes(stop.getTime());
            if (stop.getArrivedAt() != null) {
                startMinutes = toMinutes(stop.getArrivedAt().substring(11, 16));
            }

            // 끝: 우선 시작 + 머무는 시간, 실제 출발이 있으면 그걸로 덮어쓰기
            int endMinutes = startMinutes + stop.getStay();
            if (stop.getDepartedAt() != null) {
                endMinutes = toMinutes(stop.getDepartedAt().substring(11, 16));
            }

            if (startMinutes <= takenMinutes && takenMinutes < endMinutes) {   // 끝은 포함 안 함
                return stop.getId();
            }
        }
        return null; // 어느 칸에도 없으면 이동 중
    }

    // "10:20" → 620 (분)
    private int toMinutes(String hhmm) {
        int hour = Integer.parseInt(hhmm.substring(0, 2));
        int minute = Integer.parseInt(hhmm.substring(3, 5));
        return hour * 60 + minute;
    }

    // 파일이 있고, 시각·길이 목록이 파일 수와 같고, 크기가 100MB 이하인지
    private void checkFiles(List<MultipartFile> files, List<String> takenAts, List<Integer> durations) {
        if (files == null || files.isEmpty()) {
            throw fail(400, "사진이나 영상을 올려 주세요.");
        }
        if (takenAts == null || takenAts.size() != files.size() || durations == null || durations.size() != files.size()) {
            throw fail(400, "찍은 시각 개수가 파일 개수와 달라요.");
        }
        for (MultipartFile file : files) {
            if (file.getSize() > MAX_FILE_BYTES) {
                throw fail(400, "파일은 100MB까지예요.");
            }
            toMediaType(file); // 사진·영상이 아니면 여기서 400
        }
    }

    // 파일 종류: image/… 이면 photo, video/… 이면 video
    private String toMediaType(MultipartFile file) {
        String contentType = file.getContentType();   // 예: "image/jpeg", "video/mp4"
        if (contentType != null && contentType.startsWith("image/")) {
            return "photo";
        }
        if (contentType != null && contentType.startsWith("video/")) {
            return "video";
        }
        throw fail(400, "사진·영상 파일만 올릴 수 있어요.");
    }

        private String saveFile(MultipartFile file) {
        String fileName = UUID.randomUUID() + getExtension(file.getOriginalFilename()); // UUID: 절대 안 겹치는 무작위 이름
        try (InputStream in = file.getInputStream()) {  // try ( ) 안에서 연 파일은 끝나면 자동으로 닫힘
            Path folder = Paths.get(photoDir).toAbsolutePath();
            Files.createDirectories(folder);             // 폴더가 없으면 만듦
            Files.copy(in, folder.resolve(fileName));      // 폴더/새이름 으로 복사
        } catch (IOException e) {
            throw fail(500, "파일을 저장하지 못했어요.");
        }
        return fileName;
    }

    // "IMG_1234.JPG" → ".jpg", 점이 없으면 ""
    private String getExtension(String originalName) {
        if (originalName == null || originalName.lastIndexOf('.') < 0) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
    }

    // 찍은 시각: 파일 안의 EXIF(사진)·생성 시각(영상)을 먼저 보고, 없으면 브라우저가 보낸 값(파일 수정 시각)
    private String readTakenAt(MultipartFile file, String browserTakenAt) {
        Date taken = null;
        try (InputStream in = file.getInputStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(in); // 사진·영상 파일의 숨은 정보 읽기 (metadata-extractor)
            taken = readDate(metadata);
        } catch (Exception e) {
            taken = null; // 못 읽는 파일이면 브라우저 값으로
        }
        if (taken == null) {
            return toTakenAt(browserTakenAt);
        }
        LocalDateTime local = LocalDateTime.ofInstant(taken.toInstant(), ZoneId.systemDefault()); // Date → 한국 시각
        return local.format(TAKEN_AT_FORMAT);
    }

    // EXIF 찍은 시각(사진) → MP4 생성 시각 → MOV 생성 시각 순서로 찾음. 2000년 전이면 없는 값으로 봄
    private Date readDate(Metadata metadata) {
        Date taken = null;
        ExifSubIFDDirectory exif = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (exif != null) {
            taken = exif.getDateOriginal(TimeZone.getDefault()); // EXIF에는 시간대가 없어서 서버 시간대(한국)로 읽음
        }
        Mp4Directory mp4 = metadata.getFirstDirectoryOfType(Mp4Directory.class);
        if (taken == null && mp4 != null) {
            taken = mp4.getDate(Mp4Directory.TAG_CREATION_TIME);
        }
        QuickTimeDirectory mov = metadata.getFirstDirectoryOfType(QuickTimeDirectory.class);
        if (taken == null && mov != null) {
            taken = mov.getDate(QuickTimeDirectory.TAG_CREATION_TIME);
        }
        if (taken != null && taken.toInstant().atZone(ZoneId.systemDefault()).getYear() < 2000) {
            return null; // 일부 휴대폰 영상은 1904년 같은 엉뚱한 값이 들어 있음
        }
        return taken;
    }

    // 브라우저가 보낸 "2026-11-01T10:20"이 맞는 모양인지 확인 (틀리면 400)
    private String toTakenAt(String browserTakenAt) {
        try {
            return LocalDateTime.parse(browserTakenAt, TAKEN_AT_FORMAT).format(TAKEN_AT_FORMAT);
        } catch (Exception e) {
            throw fail(400, "날짜 형태를 잘못가져왔어요. 찍은 시각은 yyyy-MM-ddTHH:mm 모양이에요.");
        }
    }

    /** 한 여행의 사진·영상 전체, 찍은 순 */
    @Override
    public List<PhotoResponse> getPhotoList(long memberId, long tripId) {
        tripService.getTrip(memberId, tripId); // 내 여행인지 확인 (남의 여행이면 404)
        return photoMapper.selectPhotoList(tripId);
    }

    /** 앨범: 내 지난 여행마다 사진 앞 8장 + 개수 */
    @Override
    public List<AlbumResponse> getAlbumList(long memberId) {
        List<AlbumResponse> albums = photoMapper.selectAlbumList(memberId);
        for (AlbumResponse album : albums) {
            List<PhotoResponse> photos = photoMapper.selectPhotoList(album.getTripId());
            List<PhotoResponse> firstPhotos = new ArrayList<>();
            for (int i = 0; i < photos.size() && i < ALBUM_PHOTO_COUNT; i++) {
                firstPhotos.add(photos.get(i));
            }
            album.setPhotos(firstPhotos);
        }
        return albums;
    }

    /** 사진·영상 파일. 내 사진이 아니거나 파일이 없으면 404 */
    @Override
    public Resource getPhotoFile(long memberId, long photoId) {
        Photo photo = photoMapper.selectPhotoFile(memberId, photoId);
        if (photo == null) {
            throw fail(404, "사진을 찾을 수 없어요.");
        }
        // FileSystemResource: 폴더 속 파일 하나를 가리킴
        Resource file = new FileSystemResource(Paths.get(photoDir).toAbsolutePath().resolve(photo.getFileName()));
        if (!file.exists()) {
            throw fail(404, "사진을 찾을 수 없어요.");
        }
        return file;
    }
}