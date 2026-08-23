package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PostConsumesDirectRequestTest {
    private final Validator validator;

    public PostConsumesDirectRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("user-001");
        req.setItemId("item-001");
        req.setExternalOrderId("ext-order-001");
        req.setConsumeNum(3);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testDefaultConsumeNum() {
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        assertThat(req.getConsumeNum()).isEqualTo(1);
    }

    @Test
    public void testInvalidConsumeNum() {
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        req.setUserid("u");
        req.setItemId("i");
        req.setExternalOrderId("e");
        req.setConsumeNum(0);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    public void testMissingRequiredFields() {
        PostConsumesDirectRequest req = new PostConsumesDirectRequest();
        assertThat(validator.validate(req).size()).isGreaterThanOrEqualTo(3);
    }
}
