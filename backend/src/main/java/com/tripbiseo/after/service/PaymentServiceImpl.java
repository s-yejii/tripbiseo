package com.tripbiseo.after.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripbiseo.after.dto.CardTransaction;
import com.tripbiseo.after.dto.Companion;
import com.tripbiseo.after.dto.CompanionRequest;
import com.tripbiseo.after.dto.PayerRequest;
import com.tripbiseo.after.dto.ShareRequest;
import com.tripbiseo.after.dto.ExtraStop;
import com.tripbiseo.after.dto.Payment;
import com.tripbiseo.after.dto.PaymentRequest;
import com.tripbiseo.after.dto.PaymentResponse;
import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.dto.RetroStop;
import com.tripbiseo.after.mapper.CompanionMapper;
import com.tripbiseo.after.mapper.PaymentMapper;
import com.tripbiseo.common.exception.ApiException;
import com.tripbiseo.common.external.CodefApproval;
import com.tripbiseo.common.external.CodefClient;
import com.tripbiseo.common.util.Geo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final int CARD_DELAY_MINUTES = 20;      // 목업: 도착하고 이만큼 뒤에 결제했다고 봄
    private static final int LODGING_DELAY_MINUTES = 5;    // 목업: 숙소는 들어가자마자 결제
    private static final int LODGING_AMOUNT = 89000;       // 목업 금액(원): 숙소 1박
    private static final int CAFE_AMOUNT = 12400;          // 카페
    private static final int FOOD_AMOUNT = 28000;          // 식당
    private static final int SHOPPING_AMOUNT = 45000;      // 쇼핑
    private static final int TICKET_AMOUNT = 3000;         // 관광지 입장권
    private static final int OTHER_AMOUNT = 9800;          // 그 밖 (계획 없이 머문 곳 등)
    private static final int STORE_NAME_MAX = 100;         // store_name VARCHAR(100)
    private static final int MEMO_MAX = 200;               // memo VARCHAR(200)
    private static final int PAYER_MAX = 20;               // payer VARCHAR(20)
    private static final String DEFAULT_PAYER = "나";

    private final PaymentMapper paymentMapper;
    private final CompanionMapper companionMapper; // 같이 간 사람 (⑤ trip_companion)
    private final RetroService retroService; // 같은 ⑤: 실제 도착·머문 시간, 머문 곳 (남의 여행이면 여기서 404)
    private final CodefClient codefClient;   // CODEF 개인카드 승인내역 (11/07)

    // TODO(⑤): card 테이블(이상재 확인)이 생기면 회원마다 저장한 connectedId로 바꾸기. 그 전까지 내 PC 시험용
    @Value("${CODEF_ORGANIZATION:}")
    private String testOrganization;   // 카드사 기관코드
    @Value("${CODEF_CONNECTED_ID:}")
    private String testConnectedId;    // 카드사 연결 번호

    /** 한 여행의 결제 내역, 결제 순 */
    @Override
    public List<PaymentResponse> getPaymentList(long memberId, long tripId) {
        retroService.getRetro(memberId, tripId); // 내 여행인지 확인 (남의 여행이면 404)
        return paymentMapper.selectPaymentList(tripId);
    }

    /** 결제 하나 저장. 일정 칸은 결제 시각에 실제로 있던 곳으로 */
    @Override
    public PaymentResponse createPayment(long memberId, long tripId, PaymentRequest request) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        checkRequest(request, retro);

        Payment payment = new Payment();
        payment.setTripId(tripId);
        payment.setStopId(findStopId(retro, request.getPaidAt()));
        payment.setPaidAt(request.getPaidAt());
        payment.setStoreName(request.getStoreName().trim());
        payment.setAmount(request.getAmount());
        payment.setMethod("cash".equals(request.getMethod()) ? "cash" : "card"); // 조건 ? 참일 때 값 : 거짓일 때 값. 모르는 값이면 card
        payment.setSource(toSource(request.getSource()));
        payment.setMemo(request.getMemo());
        payment.setPayer(toPayer(request.getPayer()));
        paymentMapper.insertPayment(payment);
        return paymentMapper.selectPayment(payment.getPaymentId());
    }

    /** 결제 하나 지우기. 이 여행 결제가 아니면 404 */
    @Override
    public void deletePayment(long memberId, long tripId, long paymentId) {
        retroService.getRetro(memberId, tripId); // 내 여행인지 확인 (남의 여행이면 404)
        int deleted = paymentMapper.deletePayment(tripId, paymentId);
        if (deleted == 0) throw fail(404, "결제를 찾을 수 없어요.");
    }

    /** 결제의 낸 사람 바꾸기. 이 여행 결제가 아니면 404 */
    @Override
    public PaymentResponse updatePayer(long memberId, long tripId, long paymentId, PayerRequest request) {
        retroService.getRetro(memberId, tripId); // 내 여행인지 확인 (남의 여행이면 404)
        if (request.getPayer() == null || request.getPayer().isBlank()) throw fail(400, "낸 사람을 골라 주세요.");
        if (request.getPayer().trim().length() > PAYER_MAX) throw fail(400, "낸 사람 이름은 20자까지예요.");
        int changed = paymentMapper.updatePayer(tripId, paymentId, request.getPayer().trim());
        if (changed == 0) throw fail(404, "결제를 찾을 수 없어요.");
        return paymentMapper.selectPayment(paymentId);
    }

    /** 같이 간 사람 목록. 처음이면 여행 인원만큼 만들어 둠 (여러 줄 저장이라 @Transactional) */
    @Override
    @Transactional
    public List<Companion> getCompanionList(long memberId, long tripId) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        return loadOrCreateCompanions(tripId, retro.getPeople());
    }

    /** 같이 간 사람 추가 (맨 뒤에) */
    @Override
    @Transactional
    public List<Companion> createCompanion(long memberId, long tripId, CompanionRequest request) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        List<Companion> list = loadOrCreateCompanions(tripId, retro.getPeople());
        String name = checkName(request, list);
        Companion companion = new Companion();
        companion.setTripId(tripId);
        companion.setName(name);
        companion.setSortNo(list.get(list.size() - 1).getSortNo() + 1); // 맨 뒤 번호 + 1
        companionMapper.insertCompanion(companion);
        return companionMapper.selectCompanionList(tripId);
    }

    /** 이름 바꾸기. 결제의 낸 사람(payer)도 이름 글자라서 같이 바꿈 → 두 번 쓰니까 @Transactional */
    @Override
    @Transactional
    public List<Companion> updateCompanion(long memberId, long tripId, long companionId, CompanionRequest request) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        List<Companion> list = loadOrCreateCompanions(tripId, retro.getPeople());
        Companion target = null; // 바꿀 사람
        for (Companion companion : list) {
            if (companion.getId() == companionId) target = companion;
        }
        if (target == null) throw fail(404, "같이 간 사람을 찾을 수 없어요.");
        if (target.getSortNo() == 0) throw fail(400, "'나'는 이름을 바꿀 수 없어요.");
        String name = checkName(request, list);
        companionMapper.updateCompanionName(tripId, companionId, name);
        companionMapper.updatePayerName(tripId, target.getName(), name);
        return companionMapper.selectCompanionList(tripId);
    }

    /** 같이 간 사람 빼기. 그 사람이 낸 결제를 '나'로 바꾸고 지움 → 두 번 쓰니까 @Transactional */
    @Override
    @Transactional
    public List<Companion> deleteCompanion(long memberId, long tripId, long companionId) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        List<Companion> list = loadOrCreateCompanions(tripId, retro.getPeople());
        Companion target = null; // 뺄 사람
        for (Companion companion : list) {
            if (companion.getId() == companionId) target = companion;
        }
        if (target == null) throw fail(404, "같이 간 사람을 찾을 수 없어요.");
        if (target.getSortNo() == 0) throw fail(400, "'나'는 뺄 수 없어요.");
        companionMapper.updatePayerName(tripId, target.getName(), DEFAULT_PAYER); // 그 사람이 낸 결제 → 나
        companionMapper.deleteCompanion(tripId, companionId);
        return companionMapper.selectCompanionList(tripId);
    }

    /** 더치페이 낼 돈 정하기. null이면 자동 (나머지를 똑같이 나누는 쪽)으로 되돌림 */
    @Override
    public List<Companion> updateShare(long memberId, long tripId, long companionId, ShareRequest request) {
        retroService.getRetro(memberId, tripId); // 내 여행인지 확인
        Integer amount = request.getAmount();
        if (amount != null && amount < 0) throw fail(400, "금액은 0원 이상이에요.");
        int changed = companionMapper.updateShareAmount(tripId, companionId, amount);
        if (changed == 0) throw fail(404, "같이 간 사람을 찾을 수 없어요.");
        return companionMapper.selectCompanionList(tripId);
    }

    // 저장된 목록. 없으면 '나' + 동행 1 … (인원 − 1)을 저장하고 돌려줌. 예: 인원 3 → 나, 동행 1, 동행 2
    private List<Companion> loadOrCreateCompanions(long tripId, Integer people) {
        List<Companion> list = companionMapper.selectCompanionList(tripId);
        if (!list.isEmpty()) return list;
        int count = (people == null || people < 1) ? 1 : people;
        for (int i = 0; i < count; i++) {
            Companion companion = new Companion();
            companion.setTripId(tripId);
            companion.setName(i == 0 ? DEFAULT_PAYER : "동행 " + i);
            companion.setSortNo(i);
            companionMapper.insertCompanion(companion);
        }
        return companionMapper.selectCompanionList(tripId);
    }

    // 이름 확인: 비었나, 20자 넘나, 이미 있나. 앞뒤 빈칸 뺀 이름을 돌려줌
    private static String checkName(CompanionRequest request, List<Companion> list) {
        if (request.getName() == null || request.getName().isBlank()) throw fail(400, "이름을 적어 주세요.");
        String name = request.getName().trim();
        if (name.length() > PAYER_MAX) throw fail(400, "이름은 20자까지예요.");
        for (Companion companion : list) {
            if (companion.getName().equals(name)) throw fail(400, "같은 이름이 있어요.");
        }
        return name;
    }

    /**
     * 카드 승인내역. CODEF 데모·정식 키 + 카드사 연결이 있으면 진짜 내역,
     * 샌드박스면 연결만 확인하고(값이 **** 로 가려져 옴) 목업, 키가 없으면 목업
     */
    @Override
    public List<CardTransaction> getCardTransactionList(long memberId, long tripId) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        List<PaymentResponse> saved = paymentMapper.selectPaymentList(tripId);
        if (codefClient.isRealData()) {
            if (testConnectedId.isBlank()) throw fail(400, "카드사를 먼저 연결해 주세요.");
            return toCodefTransactions(retro, saved);
        }
        if (codefClient.isReady()) checkSandbox(retro);
        return toMockTransactions(retro, saved);
    }

    // CODEF 진짜 내역 → 우리 모양. 여행 기간 밖은 뺌
    private List<CardTransaction> toCodefTransactions(RetroResponse retro, List<PaymentResponse> saved) {
        String start = retro.getStartDate().replace("-", "");
        String end = retro.getEndDate().replace("-", "");
        List<CardTransaction> list = new ArrayList<>();
        for (CodefApproval approval : codefClient.getApprovalList(testOrganization, testConnectedId, start, end)) {
            String date = approval.getUsedDate();   // "20261104"
            String time = approval.getUsedTime();   // "113500"
            String paidAt = date.substring(0, 4) + "-" + date.substring(4, 6) + "-" + date.substring(6, 8)
                    + "T" + time.substring(0, 2) + ":" + time.substring(2, 4);
            int day = toDay(retro, paidAt);
            if (day < 1 || date.compareTo(end) > 0) continue; // 여행 기간 밖
            CardTransaction transaction = new CardTransaction();
            transaction.setApprovalDate(date);
            transaction.setApprovalTime(time);
            transaction.setPaidAt(paidAt);
            transaction.setDay(day);
            transaction.setStoreName(approval.getStoreName());
            transaction.setAmount(approval.getAmount());
            transaction.setImported(isImported(saved, transaction));
            list.add(transaction);
        }
        return list;
    }

    // 샌드박스: 진짜로 불러서 연결(토큰·호출·응답 읽기)만 확인. 실패해도 화면은 목업으로 계속
    private void checkSandbox(RetroResponse retro) {
        try {
            int count = codefClient.getApprovalList("0309", "sandbox", retro.getStartDate().replace("-", ""),
                    retro.getEndDate().replace("-", "")).size();
            log.info("CODEF 샌드박스 연결 OK (쓸 수 있는 내역 {}건, 샌드박스는 값이 가려져 와서 0건이 정상)", count);
        } catch (ApiException e) {
            log.warn("CODEF 샌드박스 연결 실패: {}", e.getMessage());
        }
    }

    // 목업: 실제로 다녀온 곳·머문 곳마다 결제 한 건씩
    private List<CardTransaction> toMockTransactions(RetroResponse retro, List<PaymentResponse> saved) {
        List<CardTransaction> list = new ArrayList<>();

        for (RetroStop stop : retro.getStops()) {
            if (stop.getActualArrive() == null) continue; // 안 간 곳은 결제도 없음
            int minutes = Geo.toMinutes(stop.getActualArrive()) + CARD_DELAY_MINUTES;
            list.add(toTransaction(retro, stop.getDay(), minutes, stop.getName(), toAmount(stop.getCategory()), saved));
        }
        for (ExtraStop extra : retro.getExtraStops()) {
            boolean isLodging = "lodging".equals(extra.getKind());
            int minutes = Geo.toMinutes(extra.getArrive()) + (isLodging ? LODGING_DELAY_MINUTES : CARD_DELAY_MINUTES);
            String name = extra.getName() != null ? extra.getName() : (isLodging ? "숙박" : "가맹점");
            list.add(toTransaction(retro, extra.getDay(), minutes, name, isLodging ? LODGING_AMOUNT : OTHER_AMOUNT, saved));
        }
        // 결제 시각 순으로. "2026-11-04T11:35"는 글자 순서 = 시간 순서라 글자로 비교해도 됨 (람다: 두 개를 받아 비교하는 짧은 함수)
        list.sort((a, b) -> a.getPaidAt().compareTo(b.getPaidAt()));
        return list;
    }

    // 요청 확인: 가게 이름·금액·결제 시각(모양, 여행 기간 안)
    private void checkRequest(PaymentRequest request, RetroResponse retro) {
        if (request.getStoreName() == null || request.getStoreName().isBlank()) throw fail(400, "가게 이름을 적어 주세요.");
        if (request.getStoreName().trim().length() > STORE_NAME_MAX) throw fail(400, "가게 이름은 100자까지예요.");
        if (request.getAmount() == null || request.getAmount() <= 0) throw fail(400, "금액은 1원 이상이에요.");
        if (request.getMemo() != null && request.getMemo().length() > MEMO_MAX) throw fail(400, "메모는 200자까지예요.");
        LocalDate date = toDate(request.getPaidAt());
        LocalDate start = LocalDate.parse(retro.getStartDate());
        LocalDate end = LocalDate.parse(retro.getEndDate());
        if (date.isBefore(start) || date.isAfter(end)) throw fail(400, "여행 기간 안의 결제만 넣을 수 있어요.");
    }

    // "2026-11-04T11:35" → 날짜. 모양이 틀리면 400
    private static LocalDate toDate(String paidAt) {
        try {
            return LocalDateTime.parse(paidAt).toLocalDate();
        } catch (DateTimeParseException | NullPointerException e) {
            throw fail(400, "결제 시각은 yyyy-MM-ddTHH:mm 모양이에요.");
        }
    }

    // 결제 시각에 실제로 있던 일정 칸 (실제 도착 ≤ 결제 < 떠남). 없으면 null = 이동 중·계획 밖
    private static Long findStopId(RetroResponse retro, String paidAt) {
        int day = toDay(retro, paidAt);
        int minutes = Geo.toMinutes(paidAt.substring(11, 16));
        for (RetroStop stop : retro.getStops()) {
            if (stop.getDay() != day) continue;
            if (stop.getActualArrive() == null || stop.getActualStay() == null) continue;
            int arrive = Geo.toMinutes(stop.getActualArrive());
            if (arrive <= minutes && minutes < arrive + stop.getActualStay()) return stop.getId();
        }
        return null;
    }

    // 몇째 날 = 결제 날짜 − 시작 날짜 + 1
    private static int toDay(RetroResponse retro, String paidAt) {
        LocalDate start = LocalDate.parse(retro.getStartDate());
        return (int) ChronoUnit.DAYS.between(start, toDate(paidAt)) + 1;
    }

    // 낸 사람: 비었으면 "나", 길면 20자까지
    private static String toPayer(String payer) {
        if (payer == null || payer.isBlank()) return DEFAULT_PAYER;
        String trimmed = payer.trim();
        if (trimmed.length() > PAYER_MAX) return trimmed.substring(0, PAYER_MAX);
        return trimmed;
    }

    // source는 정해진 세 값만. 모르는 값이면 manual
    private static String toSource(String source) {
        if ("card".equals(source) || "capture".equals(source)) return source;
        return "manual";
    }

    // 목업 금액: 분류로 대충 정함
    private static int toAmount(String category) {
        if (category == null) return OTHER_AMOUNT;
        if (category.contains("카페")) return CAFE_AMOUNT;
        if (category.contains("맛집") || category.contains("식당") || category.contains("음식")) return FOOD_AMOUNT;
        if (category.contains("쇼핑")) return SHOPPING_AMOUNT;
        if (category.contains("관광")) return TICKET_AMOUNT;
        return OTHER_AMOUNT;
    }

    // 목업 승인내역 한 줄. minutes가 1440을 넘으면 다음 날로
    private static CardTransaction toTransaction(RetroResponse retro, int day, int minutes, String storeName,
                                                 int amount, List<PaymentResponse> saved) {
        LocalDate date = LocalDate.parse(retro.getStartDate()).plusDays(day - 1 + minutes / 1440);
        int dayMinutes = minutes % 1440;
        String time = Geo.toHhmm(dayMinutes); // "11:35"
        CardTransaction transaction = new CardTransaction();
        transaction.setApprovalDate(date.toString().replace("-", ""));
        transaction.setApprovalTime(time.replace(":", "") + "00");
        transaction.setPaidAt(date + "T" + time);
        transaction.setDay(day + minutes / 1440);
        transaction.setStoreName(storeName);
        transaction.setAmount(amount);
        transaction.setImported(isImported(saved, transaction));
        return transaction;
    }

    // 이미 가져온 결제인가: 같은 날·시각·가게·금액이 결제 내역에 있으면
    private static boolean isImported(List<PaymentResponse> saved, CardTransaction transaction) {
        String time = transaction.getPaidAt().substring(11, 16);
        for (PaymentResponse payment : saved) {
            if (payment.getDay() == transaction.getDay() && payment.getPaidAt().equals(time)
                    && payment.getAmount() == transaction.getAmount() && payment.getStoreName().equals(transaction.getStoreName())) {
                return true;
            }
        }
        return false;
    }
}
