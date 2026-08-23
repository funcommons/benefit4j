package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostConsumesCommitRequestTest {
    private final Validator validator;

    public PostConsumesCommitRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostConsumesCommitRequest req = new PostConsumesCommitRequest();
        req.setExternalOrderId("ext-order-001");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testMissingExternalOrderId() {
        PostConsumesCommitRequest req = new PostConsumesCommitRequest();
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
