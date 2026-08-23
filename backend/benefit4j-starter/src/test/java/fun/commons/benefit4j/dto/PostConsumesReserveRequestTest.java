package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import jakarta.validation.ConstraintViolation;

import static org.assertj.core.api.Assertions.assertThat;

public class PostConsumesReserveRequestTest {
    private final Validator validator;

    public PostConsumesReserveRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostConsumesReserveRequest req = new PostConsumesReserveRequest();
        req.setUserid("user-001");
        req.setItemId("item-001");
        req.setExternalOrderId("ext-order-001");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testDefaultConsumeNum() {
        PostConsumesReserveRequest req = new PostConsumesReserveRequest();
        assertThat(req.getConsumeNum()).isEqualTo(1);
    }
}
