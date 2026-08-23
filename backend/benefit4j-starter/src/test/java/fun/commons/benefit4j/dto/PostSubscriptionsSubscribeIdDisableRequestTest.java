package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PostSubscriptionsSubscribeIdDisableRequestTest {
    @Test
    public void testGetterSetter() {
        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("fraud detected");
        assertThat(req.getReason()).isEqualTo("fraud detected");
    }
}
