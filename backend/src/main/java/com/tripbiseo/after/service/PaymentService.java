package com.tripbiseo.after.service;

import java.util.List;

import com.tripbiseo.after.dto.CardTransaction;
import com.tripbiseo.after.dto.PaymentRequest;
import com.tripbiseo.after.dto.PaymentResponse;

/** ⑤ 결제 내역 (회고 핀·시트에 보여 줌) */
public interface PaymentService {

    /** 한 여행의 결제 내역, 결제 순 */
    List<PaymentResponse> getPaymentList(long memberId, long tripId);

    /** 결제 하나 저장. 일정 칸(stopId)은 결제 시각으로 서버가 붙임 */
    PaymentResponse createPayment(long memberId, long tripId, PaymentRequest request);

    /** 카드 승인내역. 지금은 실제로 다녀온 곳으로 만든 목업 (나중에 CODEF) */
    List<CardTransaction> getCardTransactionList(long memberId, long tripId);
}
