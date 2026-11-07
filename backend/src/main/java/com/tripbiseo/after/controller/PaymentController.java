package com.tripbiseo.after.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.after.dto.CardTransaction;
import com.tripbiseo.after.dto.PaymentRequest;
import com.tripbiseo.after.dto.PaymentResponse;
import com.tripbiseo.after.service.PaymentService;
import com.tripbiseo.common.auth.LoginUser;

import lombok.RequiredArgsConstructor;

/** ⑤ 결제 내역 (회고 핀·시트) + 카드 승인내역 (지금은 목업) */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final LoginUser loginUser;   // 지금 로그인한 회원 (loginUser.id() = 회원 번호)

    /** 한 여행의 결제 내역, 결제 순 */
    @GetMapping("/api/trips/{id}/payments")
    public List<PaymentResponse> getPaymentList(@PathVariable long id) {
        return paymentService.getPaymentList(loginUser.id(), id);
    }

    /** 결제 하나 저장 (직접 입력 또는 카드 내역에서 가져오기) */
    @PostMapping("/api/trips/{id}/payments")
    public PaymentResponse createPayment(@PathVariable long id, @RequestBody PaymentRequest request) {
        return paymentService.createPayment(loginUser.id(), id, request);
    }

    /** 카드 승인내역 (목업. 나중에 CODEF) */
    @GetMapping("/api/trips/{id}/card-transactions")
    public List<CardTransaction> getCardTransactionList(@PathVariable long id) {
        return paymentService.getCardTransactionList(loginUser.id(), id);
    }
}
