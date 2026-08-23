package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostRefundsRequestTest {
    private final Validator validator;

    public PostRefundsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostRefundsRequest req = new PostRefundsRequest();
        req.setConsumeId("consume-001");
        req.setExternalRefundId("ext-refund-001");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testDefaultRefundNum() {
        PostRefundsRequest req = new PostRefundsRequest();
        assertThat(req.getRefundNum()).isEqualTo(1);
    }
}
