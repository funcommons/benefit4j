package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostCompensationsRequestTest {
    private final Validator validator;

    public PostCompensationsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("sub-001");
        req.setSubsItemId("sitem-001");
        req.setItemId("item-001");
        req.setAdjustNum(5);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testDefaultAdjustType() {
        PostCompensationsRequest req = new PostCompensationsRequest();
        assertThat(req.getAdjustType()).isEqualTo("ADD");
    }

    @Test
    public void testInvalidAdjustNum() {
        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("s");
        req.setSubsItemId("si");
        req.setItemId("i");
        req.setAdjustNum(0);
        assertThat(validator.validate(req)).isNotEmpty();
    }
}
