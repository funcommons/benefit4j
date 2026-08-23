package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostGlobalTemplatesRequestTest {
    private final Validator validator;

    public PostGlobalTemplatesRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostGlobalTemplatesRequest req = new PostGlobalTemplatesRequest();
        req.setName("Standard VIP");
        req.setDuration(1);
        req.setDurationUnit("month");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testDefaultValues() {
        PostGlobalTemplatesRequest req = new PostGlobalTemplatesRequest();
        assertThat(req.getPriority()).isEqualTo(0);
        assertThat(req.getQuota()).isEqualTo(0);
    }
}
