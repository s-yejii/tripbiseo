package com.tripbiseo.after.service;

import java.util.List;

import com.tripbiseo.after.dto.CardTransaction;
import com.tripbiseo.after.dto.Companion;
import com.tripbiseo.after.dto.CompanionRequest;
import com.tripbiseo.after.dto.PayerRequest;
import com.tripbiseo.after.dto.ShareRequest;
import com.tripbiseo.after.dto.PaymentRequest;
import com.tripbiseo.after.dto.PaymentResponse;

/** ⑤ 결제 내역 (회고 핀·시트에 보여 줌) */
public interface PaymentService {

    /** 한 여행의 결제 내역, 결제 순 */
    List<PaymentResponse> getPaymentList(long memberId, long tripId);

    /** 결제 하나 저장. 일정 칸(stopId)은 결제 시각으로 서버가 붙임 */
    PaymentResponse createPayment(long memberId, long tripId, PaymentRequest request);

    /** 결제 하나 지우기 */
    void deletePayment(long memberId, long tripId, long paymentId);

    /** 결제의 낸 사람 바꾸기 (정산용). 바꾼 결제를 돌려줌 */
    PaymentResponse updatePayer(long memberId, long tripId, long paymentId, PayerRequest request);

    /** 같이 간 사람 목록 (정산의 낸 사람). 처음이면 여행 인원만큼 '나', '동행 1' … 을 만들어 둠 */
    List<Companion> getCompanionList(long memberId, long tripId);

    /** 같이 간 사람 추가. 바뀐 목록을 돌려줌 */
    List<Companion> createCompanion(long memberId, long tripId, CompanionRequest request);

    /** 같이 간 사람 이름 바꾸기 (그 사람이 낸 결제의 이름도 같이). 바뀐 목록을 돌려줌 */
    List<Companion> updateCompanion(long memberId, long tripId, long companionId, CompanionRequest request);

    /** 더치페이 낼 돈 정하기 (null이면 자동). 바뀐 목록을 돌려줌 */
    List<Companion> updateShare(long memberId, long tripId, long companionId, ShareRequest request);

    /** 카드 승인내역. 지금은 실제로 다녀온 곳으로 만든 목업 (나중에 CODEF) */
    List<CardTransaction> getCardTransactionList(long memberId, long tripId);
}
