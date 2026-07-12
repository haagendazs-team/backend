import com.haagendazs.payment.PaymentApplication;
import com.haagendazs.payment.order.service.OrderService;
import com.haagendazs.payment.order.service.dto.OrderCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@TestPropertySource(locations = "file:.env")
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Test
    @DisplayName("createOrderTest 성공")

}
