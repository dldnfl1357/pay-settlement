package com.example.payment.domain;

public enum PaymentStatus {
    PENDING,    // 결제 대기
    APPROVED,   // 승인 완료
    CANCELLED,  // 취소
    FAILED      // 실패
}
