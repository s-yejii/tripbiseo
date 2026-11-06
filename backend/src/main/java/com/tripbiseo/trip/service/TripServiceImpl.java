package com.tripbiseo.trip.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.util.Geo;
import com.tripbiseo.trip.dto.ChatMessage;
import com.tripbiseo.trip.dto.Stop;
import com.tripbiseo.trip.dto.Trip;
import com.tripbiseo.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/**
 * ② 여행·일정. 모든 조회·수정은 로그인한 회원의 여행만 (남의 여행이면 404).
 * 예전 코드: git show backend-v0:backend/src/main/java/com/tripbiseo/service/TripService.java
 * 입력 검사 오류 문장은 docs/API.md 그대로.
 */
@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    private static final List<String> TRANSPORTS = List.of("bus", "subway", "walk", "taxi", "car");
    private static final List<String> MODES = List.of("walk", "transit", "taxi");
    private static final List<String> IMPORTANCES = List.of("must", "maybe", "drop"); // 중요도 꼭 / 가능하면 / 빼도 됨
    private static final String DEFAULT_IMPORTANCE = "maybe";
    private static final List<String> STATES = List.of("todo", "done", "skipped");
    private static final Logger log = LoggerFactory.getLogger(TripServiceImpl.class);
    private static final int MAX_TRASH = 20;        // 휴지통에 남길 수 (계정마다)
    private static final String STATUS_ONGOING = "ongoing";
    private static final int MAX_TRIP_DAYS = 30;
    private static final int MAX_STAY_MIN = 720;
    private static final int MAX_CHAT_LENGTH = 2000;

    private final TripMapper tripMapper;
    private final PlaceService placeService; // placeId가 DB에 있는지 확인

    /** 내 여행 목록 */
    @Override
    public List<Trip> getTripList(long memberId) {
        return tripMapper.selectTripList(memberId);
    }

    /** 여행 + 일정 칸. 없거나 남의 여행이면 404 */
    @Override
    public Trip getTrip(long memberId, long tripId) {
        Trip trip = tripMapper.selectTrip(tripId, memberId);
        if (trip == null) {
            throw fail(404, "여행을 찾을 수 없어요.");
        }
        trip.setStops(tripMapper.selectStopList(tripId));
        return trip;
    }

    /** 여행을 일정 칸까지 한 번에 저장 (여행 1번 + 칸 여러 번 쓰기라 @Transactional) */
    @Override
    @Transactional
    public Trip createTrip(long memberId, Trip trip) {
        checkTripInfo(trip);
        if (trip.getStops() == null || trip.getStops().isEmpty()) {
            throw fail(400, "장소를 하나 이상 담아 주세요.");
        }
        if (trip.getEndDate().compareTo(Geo.now().toLocalDate().toString()) < 0) {
            throw fail(400, "이미 지난 날짜로는 여행을 만들 수 없어요.");
        }
        trip.setMemberId(memberId);
        tripMapper.insertTrip(trip);
        int days = countDays(trip);
        for (Stop stop : trip.getStops()) {
            checkStop(stop, days);
            stop.setTripId(trip.getId());
            tripMapper.insertStop(stop);
        }
        return getTrip(memberId, trip.getId());
    }

    /** 여행 정보(이름·날짜·인원·수단)만 수정. 줄인 날짜 밖에 일정이 있으면 400 */
    @Override
    public Trip updateTrip(long memberId, long tripId, Trip info) {
        Trip saved = getTrip(memberId, tripId);
        checkTripInfo(info);
        int days = countDays(info);
        for (Stop stop : saved.getStops()) {
            if (stop.getDay() > days) {
                throw fail(400, "줄인 날짜 밖에 일정이 있어요. 그 일정을 먼저 옮기거나 지워 주세요.");
            }
        }
        info.setId(tripId);
        info.setMemberId(memberId);
        tripMapper.updateTrip(info);
        return getTrip(memberId, tripId);
    }

    /** 여행 삭제 = 휴지통으로 옮기기. 휴지통이 넘치면 가장 오래 전에 지운 것부터 영구 삭제 */
    @Override
    @Transactional
    public void deleteTrip(long memberId, long tripId) {
        if (tripMapper.updateTripTrashed(tripId, memberId) == 0) {
            throw fail(404, "여행을 찾을 수 없어요.");
        }
        log.info("여행 삭제 member={} trip={} 동작=휴지통으로", memberId, tripId);
        int removed = tripMapper.deleteOldTrash(memberId, MAX_TRASH);
        if (removed > 0) {
            log.info("여행 삭제 member={} 동작=휴지통이 넘쳐 오래된 {}개 영구 삭제", memberId, removed);
        }
    }

    /** 휴지통 목록 */
    @Override
    public List<Trip> getTrashList(long memberId) {
        return tripMapper.selectTrashList(memberId);
    }

    /** 휴지통에서 꺼내기. status는 그대로라 원래 상태로 돌아가요 */
    @Override
    public Trip restoreTrip(long memberId, long tripId) {
        Trip trashed = tripMapper.selectTrashTrip(tripId, memberId);
        if (trashed == null) {
            throw fail(404, "휴지통에서 여행을 찾을 수 없어요.");
        }
        boolean isOngoing = STATUS_ONGOING.equals(trashed.getStatus());
        if (isOngoing && tripMapper.selectOtherOngoingTrip(memberId, tripId) != null) {
            throw fail(409, "지금 진행 중인 여행이 있어서 복원할 수 없어요. 그 여행을 끝내거나 보관한 뒤 복원해 주세요.");
        }
        tripMapper.updateTripRestored(tripId, memberId);
        log.info("여행 복원 member={} trip={}", memberId, tripId);
        return getTrip(memberId, tripId);
    }

    /** 휴지통의 여행 영구 삭제 */
    @Override
    public void deleteTrashTrip(long memberId, long tripId) {
        if (tripMapper.deleteTrip(tripId, memberId) == 0) {
            throw fail(404, "휴지통에서 여행을 찾을 수 없어요.");
        }
        log.info("여행 영구 삭제 member={} trip={}", memberId, tripId);
    }

    /** 휴지통 비우기 */
    @Override
    public void deleteAllTrash(long memberId) {
        log.info("휴지통 비우기 member={} 개수={}", memberId, tripMapper.deleteAllTrash(memberId));
    }

    /** 여행 시작: 오늘이 여행 기간 안이고, 진행 중인 다른 여행이 없을 때만 */
    @Override
    public Trip startTrip(long memberId, long tripId) {
        Trip trip = getTrip(memberId, tripId);
        String today = Geo.now().toLocalDate().toString();
        boolean isInPeriod = today.compareTo(trip.getStartDate()) >= 0 && today.compareTo(trip.getEndDate()) <= 0;
        if (!isInPeriod) {
            throw fail(400, "여행 기간(" + trip.getStartDate() + " ~ " + trip.getEndDate() + ")에만 시작할 수 있어요.");
        }
        Trip other = tripMapper.selectOtherOngoingTrip(memberId, tripId);
        if (other != null) {
            throw fail(409, "이미 진행 중인 여행(" + other.getTitle() + ")이 있어요. 그 여행을 먼저 마쳐 주세요.");
        }
        tripMapper.updateTripStatus(tripId, "ongoing");
        return getTrip(memberId, tripId);
    }

    /** 여행 마치기 (진행 중이 아니어도 막지 않음) */
    @Override
    public Trip finishTrip(long memberId, long tripId) {
        getTrip(memberId, tripId);
        tripMapper.updateTripStatus(tripId, "done");
        return getTrip(memberId, tripId);
    }

    /** 보관: status를 planned로 되돌림. started_at이 남아 있어서 "시작한 적 있는 planned" = 보관으로 구분 (새 컬럼 없음) */
    @Override
    public Trip archiveTrip(long memberId, long tripId) {
        Trip trip = getTrip(memberId, tripId);
        if (!"ongoing".equals(trip.getStatus())) {
            throw fail(400, "진행 중인 여행만 보관할 수 있어요.");
        }
        tripMapper.updateTripStatus(tripId, "planned");
        return getTrip(memberId, tripId);
    }

    /** 일정 칸 하나 추가 (state는 항상 todo) */
    @Override
    public Trip createStop(long memberId, long tripId, Stop stop) {
        Trip trip = getTrip(memberId, tripId);
        checkStop(stop, countDays(trip));
        stop.setTripId(tripId);
        tripMapper.insertStop(stop);
        return getTrip(memberId, tripId);
    }

    /** 일정 칸 수정. day·time·stay·fixed·state는 null이면 원래 값, mode·memo는 항상 보낸 값 (API.md) */
    @Override
    public Trip updateStop(long memberId, long tripId, long stopId, Stop form) {
        Trip trip = getTrip(memberId, tripId);
        Stop stop = findStop(tripId, stopId);
        if (form.getDay() != null) {
            stop.setDay(form.getDay());
        }
        if (form.getTime() != null) {
            stop.setTime(form.getTime());
        }
        if (form.getStay() != null) {
            stop.setStay(form.getStay());
        }
        if (form.getFixed() != null) {
            stop.setFixed(form.getFixed());
        }
        if (form.getState() != null && STATES.contains(form.getState())) {
            stop.setState(form.getState());
        }
        if (form.getImportance() != null) {
            stop.setImportance(form.getImportance());
        }
        stop.setMode(form.getMode());
        stop.setMemo(form.getMemo());
        checkStop(stop, countDays(trip));
        tripMapper.updateStop(stop);
        return getTrip(memberId, tripId);
    }

    /** 일정 칸 삭제 */
    @Override
    public Trip deleteStop(long memberId, long tripId, long stopId) {
        getTrip(memberId, tripId);
        if (tripMapper.deleteStop(tripId, stopId) == 0) {
            throw fail(404, "일정을 찾을 수 없어요.");
        }
        return getTrip(memberId, tripId);
    }

    /**
     * 일정 칸 전부를 한 번에 저장 (11/04, 편집 화면 "완료")
     * 1) 보낸 칸이 모두 이 여행 칸인지 확인 2) 목록에 없는 칸은 지움 (skipped 칸은 여행 중 기록이라 남김)
     * 3) 보낸 칸마다 updateStop으로 고침 (날짜·시각 검사도 같이 됨)
     * 쓰기를 여러 번 하니까 @Transactional: 중간에 하나라도 실패하면 지운 것까지 전부 되돌려요
     */
    @Override
    @Transactional
    public Trip updateStops(long memberId, long tripId, List<Stop> stops) {
        Trip trip = getTrip(memberId, tripId);
        if (stops == null) {
            throw fail(400, "저장할 일정이 없어요.");
        }
        List<Long> keepIds = collectStopIds(trip, stops);
        deleteMissingStops(trip, keepIds);
        for (Stop stop : stops) {
            updateStop(memberId, tripId, stop.getId(), stop);
        }
        return getTrip(memberId, tripId);
    }

    /** 여러 일정 칸을 skipped로 (④ 대안 적용이 부름) */
    @Override
    @Transactional
    public void skipStops(long memberId, long tripId, List<Long> stopIds) {
        getTrip(memberId, tripId);
        if (stopIds == null || stopIds.isEmpty()) {
            return;
        }
        tripMapper.updateStopState(tripId, stopIds, "skipped");
    }

    /** GPS 도착 기록 */
    @Override
    public void arriveStop(long memberId, long tripId, long stopId) {
        getTrip(memberId, tripId);
        tripMapper.updateStopArrived(tripId, stopId, Geo.now().toLocalDateTime()); // DB NOW() 대신 앱 시계 (시연 시계와 맞게, 11/06)
    }

    /** GPS 출발 기록 (state = done) */
    @Override
    public void departStop(long memberId, long tripId, long stopId) {
        getTrip(memberId, tripId);
        tripMapper.updateStopDeparted(tripId, stopId, Geo.now().toLocalDateTime());
    }

    /** 이 여행의 대화 기록 (시간순) */
    @Override
    public List<ChatMessage> getChatList(long memberId, long tripId) {
        getTrip(memberId, tripId);
        return tripMapper.selectChatList(tripId);
    }

    /** 대화 기록 여러 줄 저장. role이 me/ai가 아니거나 text가 비면 그 줄은 건너뜀 */
    @Override
    @Transactional
    public void createChats(long memberId, long tripId, List<ChatMessage> messages) {
        getTrip(memberId, tripId);
        if (messages == null) {
            return;
        }
        for (ChatMessage message : messages) {
            if (!isSavableChat(message)) {
                continue;
            }
            ChatMessage chat = new ChatMessage();
            chat.setRole(message.getRole());
            chat.setText(cutText(message.getText().trim(), MAX_CHAT_LENGTH));
            chat.setAt(isChatTime(message.getAt()) ? message.getAt() : null); // null이면 DB가 지금 시각
            tripMapper.insertChat(tripId, chat);
        }
    }

    // ---------------------------------------------------------------- 입력 검사 (오류 문장은 API.md 그대로)

    /** 여행 정보 검사: 이름 1~60자, 날짜, 기간 1~30일, 인원 1~20, 수단 1개 이상 */
    private void checkTripInfo(Trip trip) {
        if (trip == null) {
            throw fail(400, "여행 정보가 없어요.");
        }
        if (trip.getTitle() == null || trip.getTitle().isBlank() || trip.getTitle().trim().length() > 60) {
            throw fail(400, "여행 이름을 1~60자로 적어 주세요.");
        }
        trip.setTitle(trip.getTitle().trim());
        checkPeriod(trip);
        if (trip.getPeople() == null || trip.getPeople() < 1 || trip.getPeople() > 20) {
            throw fail(400, "동행 인원은 1~20명이에요.");
        }
        boolean hasTransport = trip.getTransports() != null && !trip.getTransports().isEmpty();
        if (!hasTransport || !TRANSPORTS.containsAll(trip.getTransports())) {
            throw fail(400, "이동 수단을 하나 이상 골라 주세요.");
        }
    }

    /** 날짜 형식과 기간(1~30일) 검사 */
    private void checkPeriod(Trip trip) {
        LocalDate start;
        LocalDate end;
        try {
            start = LocalDate.parse(trip.getStartDate());
            end = LocalDate.parse(trip.getEndDate());
        } catch (DateTimeParseException | NullPointerException e) {
            throw fail(400, "여행 날짜를 확인해 주세요.");
        }
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= MAX_TRIP_DAYS) {
            throw fail(400, "여행 기간은 1~30일이에요.");
        }
    }

    /** 일정 칸 검사 + 기본값 채우기 (mode가 이상하면 null, placeId가 DB에 없으면 연결만 끊음) */
    private void checkStop(Stop stop, int days) {
        if (stop.getName() == null || stop.getName().isBlank() || stop.getName().length() > 120) {
            throw fail(400, "장소 이름을 확인해 주세요.");
        }
        String name = stop.getName();
        boolean hasPosition = stop.getLat() != null && stop.getLng() != null;
        if (!hasPosition || Math.abs(stop.getLat()) > 90 || Math.abs(stop.getLng()) > 180) {
            throw fail(400, name + "의 위치를 확인해 주세요.");
        }
        if (stop.getDay() == null || stop.getDay() < 1 || stop.getDay() > days) {
            throw fail(400, name + "의 날짜가 여행 기간 밖이에요.");
        }
        if (stop.getTime() == null || !stop.getTime().matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            throw fail(400, name + "의 시각을 HH:mm으로 적어 주세요.");
        }
        if (stop.getStay() == null || stop.getStay() < 0 || stop.getStay() > MAX_STAY_MIN) {
            throw fail(400, name + "의 머무는 시간은 0~720분이에요.");
        }
        if (stop.getMemo() != null && stop.getMemo().length() > 200) {
            throw fail(400, "메모는 200자까지예요.");
        }
        fillStopDefaults(stop);
    }

    /** 칸 기본값: mode는 walk/transit/taxi만, importance는 must/maybe/drop만(기본 maybe), fixed 기본 false, state 기본 todo, 없는 placeId는 null */
    private void fillStopDefaults(Stop stop) {
        if (stop.getMode() != null && !MODES.contains(stop.getMode())) {
            stop.setMode(null);
        }
        if (stop.getImportance() == null || !IMPORTANCES.contains(stop.getImportance())) {
            stop.setImportance(DEFAULT_IMPORTANCE);
        }
        if (stop.getFixed() == null) {
            stop.setFixed(false);
        }
        if (stop.getState() == null) {
            stop.setState("todo");
        }
        if (stop.getPlaceId() != null && !isPlaceInDb(stop.getPlaceId())) {
            stop.setPlaceId(null);
        }
    }

    /** placeId가 retrip_place에 있나 (②의 장소 서비스로 확인) */
    private boolean isPlaceInDb(String placeId) {
        try {
            return placeService.getPlace(placeId) != null;
        } catch (ApiException e) {
            return false;
        }
    }

    /** 보낸 칸 번호 목록 (번호가 없거나 이 여행 칸이 아니면 400) */
    private List<Long> collectStopIds(Trip trip, List<Stop> stops) {
        List<Long> tripStopIds = new ArrayList<>();
        for (Stop stop : trip.getStops()) {
            tripStopIds.add(stop.getId());
        }
        List<Long> keepIds = new ArrayList<>();
        for (Stop stop : stops) {
            if (stop.getId() == null || !tripStopIds.contains(stop.getId())) {
                throw fail(400, "이 여행의 일정만 보낼 수 있어요.");
            }
            keepIds.add(stop.getId());
        }
        return keepIds;
    }

    /** 이 여행 칸 중 keepIds에 없는 칸 지우기 (skipped 칸은 남김) */
    private void deleteMissingStops(Trip trip, List<Long> keepIds) {
        for (Stop stop : trip.getStops()) {
            boolean isSkipped = "skipped".equals(stop.getState());
            if (!isSkipped && !keepIds.contains(stop.getId())) {
                tripMapper.deleteStop(trip.getId(), stop.getId());
            }
        }
    }

    /** 일정 칸 하나 (없으면 404) */
    private Stop findStop(long tripId, long stopId) {
        Stop stop = tripMapper.selectStop(tripId, stopId);
        if (stop == null) {
            throw fail(404, "일정을 찾을 수 없어요.");
        }
        return stop;
    }

    /** 여행 날짜 수 (시작일 ~ 끝날 포함) */
    private int countDays(Trip trip) {
        return (int) ChronoUnit.DAYS.between(LocalDate.parse(trip.getStartDate()), LocalDate.parse(trip.getEndDate())) + 1;
    }

    /** 저장할 수 있는 대화 줄인가: role이 me/ai이고 text가 있음 */
    private boolean isSavableChat(ChatMessage message) {
        boolean hasRole = "me".equals(message.getRole()) || "ai".equals(message.getRole());
        return hasRole && message.getText() != null && !message.getText().isBlank();
    }

    /** at 형식: yyyy-MM-ddTHH:mm 또는 yyyy-MM-ddTHH:mm:ss */
    private boolean isChatTime(String at) {
        return at != null && at.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?");
    }

    /** 글자 수 자르기 */
    private String cutText(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }
}
