package com.tripbiseo.plan.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.FoundLocation;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.AiCapture;
import com.tripbiseo.plan.dto.AiStayCapture;
import com.tripbiseo.plan.dto.CaptureResponse;
import com.tripbiseo.plan.dto.StayCaptureResponse;
import com.tripbiseo.trip.dto.Place;
import com.tripbiseo.trip.service.PlaceService;

import lombok.RequiredArgsConstructor;

/**
 * ③ 예약 캡처 OCR. 별도 OCR 프로그램 대신 Gemini에 사진을 바로 보내 JSON(이름·날짜·시각·주소)으로 받아요.
 * 근거: 이미 쓰는 키 하나로 되고, 글자만 주는 OCR과 달리 "어느 줄이 예약 시각인지"까지 골라 줌.
 * 검증: 형식이 틀린 날짜·시각은 버리고(null), 이름이 없으면 422. 결과는 저장하지 않고 사용자가 확인한 뒤 넣어요.
 * 개인정보: 예약 번호·전화번호·카드 번호는 받지 않음 (프롬프트에서 세 가지만 요청), 사진은 서버에 남기지 않음.
 */
@Service
@RequiredArgsConstructor
public class CaptureServiceImpl implements CaptureService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final List<String> IMAGE_TYPES = List.of("image/png", "image/jpeg", "image/webp");
    private static final String DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}";
    private static final String TIME_PATTERN = "([01]\\d|2[0-3]):[0-5]\\d";
    private static final int MAX_QUERY_LENGTH = 40; // 위치 검색어 길이 제한 (② 검색과 같음)

    private final AiService aiService;
    private final PlaceService placeService;

    /** 사진 검사 → Gemini로 읽기 → 형식 검사 → DB 장소 찾기 */
    @Override
    public CaptureResponse readCapture(byte[] image, String imageType) {
        checkImage(image, imageType);
        AiCapture capture = aiService.askJsonWithImage(makePrompt(), image, imageType, AiCapture.class);
        if (capture == null || capture.getName() == null || capture.getName().isBlank()) {
            throw fail(422, "예약 정보를 읽지 못했어요. 장소 이름과 시간이 보이게 다시 찍어 주세요.");
        }
        CaptureResponse response = new CaptureResponse();
        response.setName(capture.getName().trim());
        response.setDate(matchesOrNull(capture.getDate(), DATE_PATTERN));
        response.setTime(matchesOrNull(capture.getTime(), TIME_PATTERN));
        response.setAddress(capture.getAddress());
        fillPlace(response);
        return response;
    }

    /** 숙소 캡처: 사진 검사 → Gemini로 읽기 → 형식 검사 → 주소(없으면 이름)로 위치 찾기 */
    @Override
    public StayCaptureResponse readStayCapture(byte[] image, String imageType) {
        checkImage(image, imageType);
        AiStayCapture capture = aiService.askJsonWithImage(makeStayPrompt(), image, imageType, AiStayCapture.class);
        if (capture == null || capture.getName() == null || capture.getName().isBlank()) {
            throw fail(422, "숙소 예약 정보를 읽지 못했어요. 숙소 이름과 체크인 날짜가 보이게 다시 찍어 주세요.");
        }
        StayCaptureResponse response = new StayCaptureResponse();
        response.setName(capture.getName().trim());
        response.setAddress(capture.getAddress());
        response.setCheckInDate(matchesOrNull(capture.getCheckInDate(), DATE_PATTERN));
        response.setCheckInTime(matchesOrNull(capture.getCheckInTime(), TIME_PATTERN));
        response.setCheckOutDate(matchesOrNull(capture.getCheckOutDate(), DATE_PATTERN));
        response.setCheckOutTime(matchesOrNull(capture.getCheckOutTime(), TIME_PATTERN));
        fillStayLocation(response);
        return response;
    }

    // ---------------------------------------------------------------- 도우미

    /** 사진이 있고, 5MB 이하이고, PNG·JPG·WEBP인지 */
    private void checkImage(byte[] image, String imageType) {
        if (image == null || image.length == 0) {
            throw fail(400, "사진을 올려 주세요.");
        }
        if (image.length > MAX_IMAGE_BYTES) {
            throw fail(400, "사진은 5MB까지예요.");
        }
        if (imageType == null || !IMAGE_TYPES.contains(imageType)) {
            throw fail(400, "PNG·JPG 사진만 올릴 수 있어요.");
        }
    }

    /** 필요한 네 가지만 JSON으로 (예약 번호·전화번호·결제 정보는 받지 않음) */
    private String makePrompt() {
        return "이 사진은 공연·식당·전시 같은 예약 확인 화면이야. 아래 네 가지만 읽어서 JSON으로 답해. 안 보이면 null.\n"
             + "예약 번호, 전화번호, 이름, 결제·카드 정보는 적지 마.\n"
             + "{\"name\": \"장소 또는 공연 이름\", \"date\": \"YYYY-MM-DD\", \"time\": \"HH:mm (입장·예약 시각)\", \"address\": \"주소\"}";
    }

    /** 숙소 예약 화면에서 여섯 가지만 (예약 번호·이름·전화번호·결제 정보는 받지 않음). 연도가 없는 화면(11.14(토))이 많아서 오늘 날짜를 알려 줌 */
    private String makeStayPrompt() {
        return "이 사진은 호텔·모텔·게스트하우스 같은 숙소 예약 확인 화면이야 (야놀자·여기어때·아고다·트립닷컴·네이버 등). "
             + "아래 여섯 가지만 읽어서 JSON으로 답해. 안 보이면 null.\n"
             + "날짜는 연·월·일을 모두 채워. 화면에 연도가 없으면 오늘(" + Geo.now().toLocalDate() + ") 이후 가장 가까운 그 날짜의 연도로 채워.\n"
             + "예약 번호, 예약자 이름, 전화번호, 결제·카드 정보는 적지 마. 객실 이름은 숙소 이름에 넣지 마.\n"
             + "{\"name\": \"숙소 이름\", \"address\": \"숙소 주소\", \"checkInDate\": \"YYYY-MM-DD\", \"checkInTime\": \"HH:mm\", "
             + "\"checkOutDate\": \"YYYY-MM-DD\", \"checkOutTime\": \"HH:mm\"}";
    }

    /** 주소로 먼저, 못 찾으면 숙소 이름으로 카카오에서 위치를 찾아 좌표를 붙임 (못 찾으면 좌표 없이) */
    private void fillStayLocation(StayCaptureResponse response) {
        FoundLocation location = findFirstLocation(response.getAddress());
        if (location == null) {
            location = findFirstLocation(response.getName());
        }
        if (location == null) {
            return;
        }
        response.setLat(location.getLat());
        response.setLng(location.getLng());
        if (response.getAddress() == null || response.getAddress().isBlank()) {
            response.setAddress(location.getAddress());
        }
    }

    /** 카카오 위치 검색 첫 결과 (검색어가 비었거나 결과가 없거나 실패하면 null) */
    private FoundLocation findFirstLocation(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            List<FoundLocation> locations = placeService.getLocationList(query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query, null, null);
            return locations.isEmpty() ? null : locations.get(0);
        } catch (ApiException e) {
            return null; // 위치를 못 찾아도 읽은 값은 돌려주고 화면에서 지도를 눌러 정함
        }
    }

    private String matchesOrNull(String value, String pattern) {
        return value != null && value.trim().matches(pattern) ? value.trim() : null;
    }

    /** 이름으로 우리 DB(없으면 TourAPI·카카오) 장소를 찾아 좌표를 붙임. 못 찾으면 좌표 없이 */
    private void fillPlace(CaptureResponse response) {
        Place place = placeService.getPlaceByName(response.getName(), null, null);
        if (place == null) {
            return;
        }
        response.setPlaceId(place.getId());
        response.setLat(place.getLat());
        response.setLng(place.getLng());
        if (response.getAddress() == null) {
            response.setAddress(place.getAddress());
        }
    }
}
