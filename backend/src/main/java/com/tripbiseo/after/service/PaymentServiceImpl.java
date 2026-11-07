package com.tripbiseo.after.service;

import static com.tripbiseo.common.exception.ApiException.fail;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tripbiseo.after.dto.CardTransaction;
import com.tripbiseo.after.dto.ExtraStop;
import com.tripbiseo.after.dto.Payment;
import com.tripbiseo.after.dto.PaymentRequest;
import com.tripbiseo.after.dto.PaymentResponse;
import com.tripbiseo.after.dto.RetroResponse;
import com.tripbiseo.after.dto.RetroStop;
import com.tripbiseo.after.mapper.PaymentMapper;
import com.tripbiseo.common.util.Geo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

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

    private final PaymentMapper paymentMapper;
    private final RetroService retroService; // 같은 ⑤: 실제 도착·머문 시간, 머문 곳 (남의 여행이면 여기서 404)

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
        paymentMapper.insertPayment(payment);
        return paymentMapper.selectPayment(payment.getPaymentId());
    }

    /** 카드 승인내역 목업: 실제로 다녀온 곳·머문 곳마다 결제 한 건씩 (나중에 CODEF를 부르게 이 메서드만 바꿈) */
    @Override
    public List<CardTransaction> getCardTransactionList(long memberId, long tripId) {
        RetroResponse retro = retroService.getRetro(memberId, tripId);
        List<PaymentResponse> saved = paymentMapper.selectPaymentList(tripId);
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
