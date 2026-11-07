package com.tripbiseo.after.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tripbiseo.after.dto.CardTransaction;
import com.tripbiseo.after.dto.Companion;
import com.tripbiseo.after.dto.CompanionRequest;
import com.tripbiseo.after.dto.PayerRequest;
import com.tripbiseo.after.dto.ShareRequest;
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

    /** 결제 하나 지우기 */
    @DeleteMapping("/api/trips/{id}/payments/{paymentId}")
    public void deletePayment(@PathVariable long id, @PathVariable long paymentId) {
        paymentService.deletePayment(loginUser.id(), id, paymentId);
    }

    /** 결제의 낸 사람 바꾸기 (정산용) */
    @PutMapping("/api/trips/{id}/payments/{paymentId}/payer")
    public PaymentResponse updatePayer(@PathVariable long id, @PathVariable long paymentId, @RequestBody PayerRequest request) {
        return paymentService.updatePayer(loginUser.id(), id, paymentId, request);
    }

    /** 같이 간 사람 목록 (처음이면 여행 인원만큼 만들어 둠) */
    @GetMapping("/api/trips/{id}/companions")
    public List<Companion> getCompanionList(@PathVariable long id) {
        return paymentService.getCompanionList(loginUser.id(), id);
    }

    /** 같이 간 사람 추가 */
    @PostMapping("/api/trips/{id}/companions")
    public List<Companion> createCompanion(@PathVariable long id, @RequestBody CompanionRequest request) {
        return paymentService.createCompanion(loginUser.id(), id, request);
    }

    /** 같이 간 사람 이름 바꾸기 */
    @PutMapping("/api/trips/{id}/companions/{companionId}")
    public List<Companion> updateCompanion(@PathVariable long id, @PathVariable long companionId, @RequestBody CompanionRequest request) {
        return paymentService.updateCompanion(loginUser.id(), id, companionId, request);
    }

    /** 같이 간 사람 빼기 (그 사람이 낸 결제는 '나'로) */
    @DeleteMapping("/api/trips/{id}/companions/{companionId}")
    public List<Companion> deleteCompanion(@PathVariable long id, @PathVariable long companionId) {
        return paymentService.deleteCompanion(loginUser.id(), id, companionId);
    }

    /** 더치페이 낼 돈 정하기 (amount가 null이면 자동) */
    @PutMapping("/api/trips/{id}/companions/{companionId}/share")
    public List<Companion> updateShare(@PathVariable long id, @PathVariable long companionId, @RequestBody ShareRequest request) {
        return paymentService.updateShare(loginUser.id(), id, companionId, request);
    }

    /** 카드 승인내역 (목업. 나중에 CODEF) */
    @GetMapping("/api/trips/{id}/card-transactions")
    public List<CardTransaction> getCardTransactionList(@PathVariable long id) {
        return paymentService.getCardTransactionList(loginUser.id(), id);
    }
}
