package com.tripbiseo.plan.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.common.ai.AiService;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.FoundLocation;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.plan.dto.AiCapture;
import com.tripbiseo.plan.dto.AiReceipt;
import com.tripbiseo.plan.dto.AiStayCapture;
import com.tripbiseo.plan.dto.CaptureResponse;
import com.tripbiseo.plan.dto.ReceiptResponse;
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
    private static final int SAME_ADDRESS_RADIUS_M = 30; // 주소 좌표에서 이 안의 가게면 "그 주소의 가게"로 봄
    // 가게 이름이 아닌 일반 단어 (이 단어들을 빼고 남는 글자가 2자 미만이면 가게 이름이 없는 문구로 봄)
    private static final List<String> GENERIC_WORDS = List.of("예약", "확정", "완료", "안내", "확인", "변경", "알림", "문자", "내역",
            "방문", "디저트", "식당", "음식점", "맛집", "카페", "레스토랑");
    private static final int MIN_STORE_NAME_LENGTH = 2;
    private static final long MAX_AMOUNT = 10_000_000; // 영수증 합계 상한 (1천만 원). 넘으면 잘못 읽은 것으로 봄

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

    /** 영수증: 사진 검사 → Gemini로 읽기 → 금액·시각 검사 → DB 장소 찾기 (예약 캡처와 같은 장소 찾기) */
    @Override
    public ReceiptResponse readReceipt(byte[] image, String imageType) {
        checkImage(image, imageType);
        AiReceipt receipt = aiService.askJsonWithImage(makeReceiptPrompt(), image, imageType, AiReceipt.class);
        if (receipt == null || receipt.getStoreName() == null || receipt.getStoreName().isBlank()) {
            throw fail(422, "영수증을 읽지 못했어요. 가게 이름과 합계 금액이 보이게 다시 찍어 주세요.");
        }
        ReceiptResponse response = new ReceiptResponse();
        response.setTotalAmount(toAmount(receipt.getTotalAmount()));
        String date = matchesOrNull(receipt.getPaidDate(), DATE_PATTERN);
        String time = matchesOrNull(receipt.getPaidTime(), TIME_PATTERN);
        response.setPaidAt(date == null || time == null ? null : date + " " + time);
        fillReceiptPlace(response, receipt);
        fillCheckNeeded(response, Boolean.TRUE.equals(receipt.getIsSumDifferent()));
        return response;
    }

    // ---------------------------------------------------------------- 도우미

    /** 영수증에서 필요한 다섯 가지만 (카드번호·승인번호·가맹점번호·사업자번호·전화번호는 받지 않음) */
    private String makeReceiptPrompt() {
        return "이 사진은 식당·카페·가게의 영수증이야. 아래 값만 읽어서 JSON으로 답해. 안 보이면 null.\n"
             + "totalAmount는 '합계'·'결제 금액'·'받을 금액' 줄의 최종 금액(할인 뒤, 숫자만)이야. 품목 금액이나 부가세 줄이 아니야.\n"
             + "품목 금액을 더한 값(할인 반영)이 합계와 다르면 isSumDifferent를 true로.\n"
             + "날짜는 연·월·일을 모두 채워. 연도가 없으면 오늘(" + Geo.now().toLocalDate() + ")과 가장 가까운 그 날짜의 연도로 채워.\n"
             + "카드번호, 승인번호, 가맹점번호, 사업자번호, 전화번호, 사람 이름은 적지 마.\n"
             + "{\"storeName\": \"가게 이름\", \"paidDate\": \"YYYY-MM-DD\", \"paidTime\": \"HH:mm\", \"totalAmount\": \"합계 금액\", "
             + "\"address\": \"가게 주소\", \"isSumDifferent\": false}";
    }

    /** "150,000원" → 150000. 숫자가 아니거나 1원~1천만 원 밖이면 null (사람이 확인) */
    private Integer toAmount(String text) {
        if (text == null) {
            return null;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > String.valueOf(MAX_AMOUNT).length()) {
            return null;
        }
        long amount = Long.parseLong(digits);
        return amount < 1 || amount > MAX_AMOUNT ? null : (int) amount;
    }

    /** 가게 이름·주소로 장소 찾기: 예약 캡처의 fillPlace를 그대로 써요 (같은 지점 고르기·주소 보정·DB 정식 이름) */
    private void fillReceiptPlace(ReceiptResponse response, AiReceipt receipt) {
        CaptureResponse place = new CaptureResponse();
        place.setName(receipt.getStoreName().trim());
        place.setAddress(receipt.getAddress());
        fillPlace(place);
        response.setStoreName(place.getName());
        response.setAddress(place.getAddress());
        response.setPlaceId(place.getPlaceId());
        response.setLat(place.getLat());
        response.setLng(place.getLng());
    }

    /** 금액·결제 시각을 못 읽었거나 품목 합이 합계와 다르면 사람이 꼭 확인하게 표시 */
    private void fillCheckNeeded(ReceiptResponse response, boolean isSumDifferent) {
        if (response.getTotalAmount() == null) {
            response.setNote("합계 금액을 못 읽었어요 · 금액을 적어 주세요");
        } else if (isSumDifferent) {
            response.setNote("품목을 더한 값과 합계가 달라요 · 금액을 확인해 주세요");
        } else if (response.getPaidAt() == null) {
            response.setNote("결제 시각을 못 읽었어요 · 시각을 확인해 주세요");
        }
        response.setCheckNeeded(response.getNote() != null);
    }

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
             + "예약이 여러 개 보이면 맨 위 첫 번째 예약 하나만 읽고, 배열이 아니라 객체 하나로 답해.\n"
             + "날짜는 연·월·일을 모두 채워. 화면에 연도가 없으면 오늘(" + Geo.now().toLocalDate() + ") 이후 가장 가까운 그 날짜의 연도로 채워.\n"
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
        // 주소를 읽었으면 그 위치 가까운 지점으로 (11/11: "산청숯불가든"만으로 찾으면 을지로 대신 마곡 지점이 잡혔어요)
        FoundLocation near = findFirstLocation(response.getAddress());
        Double nearLat = near == null ? null : near.getLat();
        Double nearLng = near == null ? null : near.getLng();
        Place place = placeService.getPlaceByName(response.getName(), nearLat, nearLng);
        if (!isSameStore(place, response.getName())) {
            place = findWhenNameMissed(response, place, near);
        }
        if (place == null) {
            return;
        }
        response.setPlaceId(place.getId());
        // 읽은 이름과 DB 이름이 서로 포함 관계면 DB 이름으로 ("산청숯불가든" → "산청숯불가든 을지로", "하나마토 문래 예약" → "하나마토 문래").
        // 일정 만들 때 이름으로 다시 찾아도 같은 지점이 되게
        if (isSameStore(place, response.getName())) {
            response.setName(place.getName());
        }
        response.setLat(place.getLat());
        response.setLng(place.getLng());
        if (response.getAddress() == null) {
            response.setAddress(place.getAddress());
        }
    }

    /**
     * 이름으로 우리 DB 가게를 못 찾았을 때 (11/12 정답지).
     * 읽은 이름이 "디저트 예약"처럼 가게 이름이 아닌 문구면 주소 자리(30m 안)의 DB 가게로.
     * 가게 이름이 따로 있으면 같은 건물의 다른 가게로 바꾸지 않고, 주소 좌표만 붙이고 placeId는 비움 (화면이 "DB에 없는 곳" 안내)
     */
    private Place findWhenNameMissed(CaptureResponse response, Place byName, FoundLocation near) {
        if (near == null) {
            // 주소가 없으면 엉뚱한 DB 가게는 버리고, 외부 검색 결과(좌표만 있는 곳)는 그대로
            return byName != null && byName.getId() == null ? byName : null;
        }
        if (isGenericName(response.getName())) {
            Place atAddress = findAtAddress(near);
            if (atAddress != null) {
                response.setName(atAddress.getName());
                return atAddress;
            }
        }
        response.setLat(near.getLat());
        response.setLng(near.getLng());
        return null;
    }

    /** "디저트 예약", "[예약 확정]"처럼 일반 단어만 있고 가게 이름이 없는지 */
    private boolean isGenericName(String name) {
        String rest = name.replaceAll("[\\s\\[\\]()·:.,!-]", ""); // 띄어쓰기·괄호·문장부호 빼기
        for (String word : GENERIC_WORDS) {
            rest = rest.replace(word, "");
        }
        return rest.length() < MIN_STORE_NAME_LENGTH;
    }

    /** 찾은 곳이 우리 DB 가게이고, 읽은 이름과 DB 이름이 한쪽에 다른 쪽이 들어 있는 사이인지 (띄어쓰기 무시) */
    private boolean isSameStore(Place place, String readName) {
        if (place == null || place.getId() == null || place.getName() == null) {
            return false;
        }
        String dbName = place.getName().replace(" ", "");
        String name = readName.replace(" ", "");
        return dbName.contains(name) || name.contains(dbName);
    }

    /** 주소 좌표 30m 안의 DB 가게 중 가장 가까운 곳 (② getNearbyPlaceList가 PostGIS로 가까운 순 정렬). 없으면 null */
    private Place findAtAddress(FoundLocation near) {
        List<Place> places = placeService.getNearbyPlaceList(near.getLat(), near.getLng(), SAME_ADDRESS_RADIUS_M, null);
        return places.isEmpty() ? null : places.get(0);
    }
}
