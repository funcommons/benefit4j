package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PostBenefitItemsRequestTest {
    private final Validator validator;

    public PostBenefitItemsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("免邮特权");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testNameTooShort() {
        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("a");
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    public void testNameTooLong() {
        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("x".repeat(51));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    public void testDefaultValues() {
        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        assertThat(req.getDefaultDeduction()).isEqualTo(1);
        assertThat(req.getStatus()).isEqualTo("ACTIVE");
    }
}
