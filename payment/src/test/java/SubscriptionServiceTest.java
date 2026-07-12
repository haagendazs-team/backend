import com.haagendazs.payment.subscription.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestPropertySource(locations = "file:.env")
public class SubscriptionServiceTest {

    @Autowired
    private SubscriptionService subscriptionService;


}
