package com.tripbiseo.after.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.tripbiseo.after.dto.PhotoResponse;
import com.tripbiseo.after.dto.ShortsAiAnswer;
import com.tripbiseo.after.dto.ShortsCaption;
import com.tripbiseo.after.dto.ShortsResponse;
import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.service.TripService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ShortsServiceImpl implements ShortsService {

    private static final int AI_PHOTO_MAX = 12;        // Gemini에 보낼 사진 수 (넘는 사진은 기본 자막)
    private static final int AI_IMAGE_WIDTH = 768;     // 보낼 때 줄일 가로 크기(px). 작을수록 빠르고 싸요
    private static final String DEFAULT_MOOD = "잔잔함";
    private static final List<String> MOODS = List.of("잔잔함", "신남", "설렘", "뭉클");
    private static final int MORNING_END_HOUR = 12;    // 12시 전이면 "오전"
    private static final int AFTERNOON_END_HOUR = 17;  // 17시 전이면 "오후", 그 뒤는 "저녁"

    private final PhotoService photoService;
    private final TripService tripService;
    private final AiService aiService;

    // 한 번 만든 결과를 기억 (여행 번호 → 결과). 서버를 끄면 사라져요
    // ConcurrentHashMap: 여러 사람이 동시에 불러도 안전한 HashMap
    private final Map<Long, ShortsResponse> cache = new ConcurrentHashMap<>();

    /** 한 여행의 쇼츠 자막·분위기. 사진 수가 그대로면 기억해 둔 것을 줘요 */
    @Override
    public ShortsResponse getShorts(long memberId, long tripId) {
        List<PhotoResponse> photos = photoService.getPhotoList(memberId, tripId); // 남의 여행이면 여기서 404
        ShortsResponse saved = cache.get(tripId);
        if (saved != null && saved.getCaptions().size() == photos.size()) {
            return saved; // 사진이 늘거나 줄었으면 다시 만듦
        }
        ShortsResponse response = makeShorts(memberId, tripId, photos);
        cache.put(tripId, response);
        return response;
    }

    // 사진마다 자막을 만들고, 사진 분위기 중 가장 많이 나온 것을 여행 분위기로
    private ShortsResponse makeShorts(long memberId, long tripId, List<PhotoResponse> photos) {
        Map<Long, String> stopNames = getStopNames(memberId, tripId);
        Map<String, Integer> moodCounts = new HashMap<>(); // 분위기 → 나온 횟수
        List<ShortsCaption> captions = new ArrayList<>();

        for (int i = 0; i < photos.size(); i++) {
            PhotoResponse photo = photos.get(i);
            String place = stopNames.get(photo.getStopId()); // 이동 중에 찍었으면 null
            ShortsAiAnswer answer = null;
            if (i < AI_PHOTO_MAX) {
                answer = askAi(memberId, photo, place);
            }

            ShortsCaption caption = new ShortsCaption();
            caption.setPhotoId(photo.getId());
            caption.setCaption(makeDefaultCaption(place, photo.getTakenAt()));
            if (answer != null) {
                caption.setCaption(answer.getCaption());
                moodCounts.put(answer.getMood(), moodCounts.getOrDefault(answer.getMood(), 0) + 1);
            }
            captions.add(caption);
        }

        ShortsResponse response = new ShortsResponse();
        response.setMood(findTopMood(moodCounts));
        response.setCaptions(captions);
        return response;
    }

    // 사진 한 장을 Gemini에 물어봄. 키가 없거나, 영상이거나, 실패하거나, 답이 이상하면 null (→ 기본 자막)
    private ShortsAiAnswer askAi(long memberId, PhotoResponse photo, String place) {
        if (!aiService.isReady() || "video".equals(photo.getMediaType())) {
            return null;
        }
        try {
            byte[] image = toSafeJpeg(memberId, photo.getId());
            if (image == null) {
                return null;
            }
            ShortsAiAnswer answer = aiService.askJsonWithImage(makePrompt(place, photo.getTakenAt()), image, "image/jpeg", ShortsAiAnswer.class);
            if (answer == null || answer.getCaption() == null || answer.getCaption().isBlank()) {
                return null;
            }
            if (!MOODS.contains(answer.getMood())) {
                answer.setMood(DEFAULT_MOOD); // 목록에 없는 분위기를 쓰면 기본값으로
            }
            return answer;
        } catch (Exception e) {
            return null; // Gemini 오류·시간 초과 → 기본 자막
        }
    }

    // Gemini에 보낼 질문. 장소 이름과 시간대만 알려 주고, 좌표·날짜·사람 정보는 보내지 않아요
    private String makePrompt(String place, String takenAt) {
        String hint = "시간대: " + toTimeOfDay(takenAt);
        if (place != null) {
            hint = "찍은 곳: " + place + " / " + hint;
        }
        return "여행 사진 한 장을 보고 쇼츠 영상에 띄울 감성 자막을 써 줘.\n"
             + hint + "\n"
             + "- caption: 한국어 한 줄, 20자 안쪽. 사진에 실제로 보이는 것(빛, 날씨, 풍경, 음식, 분위기)을 담아서.\n"
             + "- 해시태그, 이모지, 따옴표는 쓰지 마. 사람이 보여도 누구인지 추측하지 마.\n"
             + "- mood: 잔잔함, 신남, 설렘, 뭉클 중 사진에 가장 어울리는 하나.\n"
             + "JSON으로만 답해: {\"caption\": \"...\", \"mood\": \"...\"}";
    }

    // 사진 파일을 가로 768px JPEG로 다시 그려요.
    // 다시 그리면 픽셀만 남고 파일 속 숨은 정보(EXIF: GPS 좌표, 기종 등)는 빠져요 → 좌표를 Gemini에 보내지 않음
    // 못 읽는 형식(HEIC 등)이면 null
    private byte[] toSafeJpeg(long memberId, long photoId) throws IOException {
        Resource file = photoService.getPhotoFile(memberId, photoId);
        BufferedImage original;
        try (InputStream in = file.getInputStream()) {
            original = ImageIO.read(in); // ImageIO: 자바 기본 그림 읽기·쓰기 도구
        }
        if (original == null) {
            return null;
        }
        int width = Math.min(AI_IMAGE_WIDTH, original.getWidth());
        int height = original.getHeight() * width / original.getWidth(); // 비율 그대로

        // BufferedImage: 메모리 속 빈 그림판. 여기에 원본을 줄여서 그려요 (RGB: 투명 없는 보통 사진)
        BufferedImage small = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = small.createGraphics();
        pen.drawImage(original, 0, 0, width, height, null);
        pen.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream(); // 파일 대신 메모리에 저장
        ImageIO.write(small, "jpg", out);
        return out.toByteArray();
    }

    // Gemini를 못 쓸 때 자막: "경복궁에서 보낸 오후" / 장소를 모르면 "이동하며 담은 오후"
    private String makeDefaultCaption(String place, String takenAt) {
        if (place == null) {
            return "이동하며 담은 " + toTimeOfDay(takenAt);
        }
        return place + "에서 보낸 " + toTimeOfDay(takenAt);
    }

    // "13:20" → "오후"
    private String toTimeOfDay(String takenAt) {
        int hour = Integer.parseInt(takenAt.substring(0, 2));
        if (hour < MORNING_END_HOUR) return "오전";
        if (hour < AFTERNOON_END_HOUR) return "오후";
        return "저녁";
    }

    // 가장 많이 나온 분위기. 하나도 없으면(키 없음 등) 잔잔함
    private String findTopMood(Map<String, Integer> moodCounts) {
        String topMood = DEFAULT_MOOD;
        int topCount = 0;
        for (String mood : moodCounts.keySet()) {
            if (moodCounts.get(mood) > topCount) {
                topMood = mood;
                topCount = moodCounts.get(mood);
            }
        }
        return topMood;
    }

    // 일정 칸 번호 → 장소 이름
    private Map<Long, String> getStopNames(long memberId, long tripId) {
        Trip trip = tripService.getTrip(memberId, tripId);
        Map<Long, String> names = new HashMap<>();
        for (Stop stop : trip.getStops()) {
            names.put(stop.getId(), stop.getName());
        }
        return names;
    }
}
