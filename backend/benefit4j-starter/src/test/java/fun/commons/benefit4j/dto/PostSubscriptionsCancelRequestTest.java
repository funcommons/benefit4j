package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostSubscriptionsCancelRequestTest {
    private final Validator validator;

    public PostSubscriptionsCancelRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        req.setSubscribeId("sub-001");
        req.setExternalOrderId("ext-order-001");
        req.setReason("no longer needed");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testMissingRequiredFields() {
        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        assertThat(validator.validate(req).size()).isGreaterThanOrEqualTo(2);
    }
}
