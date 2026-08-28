package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostTenantsRequestTest {
    private final Validator validator;

    public PostTenantsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostTenantsRequest req = new PostTenantsRequest();
        req.setName("Test App");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testMissingName() {
        PostTenantsRequest req = new PostTenantsRequest();
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
