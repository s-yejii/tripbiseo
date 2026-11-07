package com.tripbiseo.after.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    /** 결제 하나 지우기. 이 여행 결제가 아니면 0줄 */
    int deletePayment(@Param("tripId") long tripId, @Param("paymentId") long paymentId);

    /** 낸 사람 바꾸기. 이 여행의 결제가 아니면 0줄 */
    int updatePayer(@Param("tripId") long tripId, @Param("paymentId") long paymentId, @Param("payer") String payer);
}
