package com.tripbiseo.after.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.tripbiseo.after.dto.Payment;
import com.tripbiseo.after.dto.PaymentResponse;

/** ⑤ 결제 내역 (payment). SQL은 resources/mapper/after/PaymentMapper.xml */
@Mapper
public interface PaymentMapper {

    /** 결제 한 줄 저장. 저장하면 payment.paymentId에 새 번호가 들어옴 */
    int insertPayment(Payment payment);

    /** 한 여행의 결제 내역, 결제 순 */
    List<PaymentResponse> selectPaymentList(long tripId);

    /** 결제 하나 (방금 저장한 것을 화면 모양으로 돌려줄 때) */
    PaymentResponse selectPayment(long paymentId);
}
