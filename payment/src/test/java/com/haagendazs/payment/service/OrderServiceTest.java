package com.haagendazs.payment.service;

import com.haagendazs.payment.TestPaymentApplication;
import com.haagendazs.payment.order.service.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(classes = TestPaymentApplication.class)
@Transactional
@ActiveProfiles("test")
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    //createOrder 테스트
    @Test
    @DisplayName("createOrderTest 성공")
    void createOrderTest() {
        // TODO: 주문 생성 성공 케이스 작성
    }
}
