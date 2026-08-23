package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostApplicationsRequestTest {
    private final Validator validator;

    public PostApplicationsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostApplicationsRequest req = new PostApplicationsRequest();
        req.setName("Test App");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testMissingName() {
        PostApplicationsRequest req = new PostApplicationsRequest();
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
