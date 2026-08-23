package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostConsumesReleaseRequestTest {
    private final Validator validator;

    public PostConsumesReleaseRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostConsumesReleaseRequest req = new PostConsumesReleaseRequest();
        req.setExternalOrderId("ext-order-001");
        assertThat(validator.validate(req)).isEmpty();
    }
}
