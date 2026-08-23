package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PostSubscriptionsRequestTest {
    private final Validator validator;

    public PostSubscriptionsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("user-001");
        req.setSetId("set-001");
        req.setExternalOrderId("ext-order-001");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testMissingRequiredFields() {
        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        Set<ConstraintViolation<PostSubscriptionsRequest>> violations = validator.validate(req);
        assertThat(violations.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    public void testExternalOrderIdTooLong() {
        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("u");
        req.setSetId("s");
        req.setExternalOrderId("x".repeat(65));
        Set<ConstraintViolation<PostSubscriptionsRequest>> violations = validator.validate(req);
        assertThat(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("externalOrderId"))).isTrue();
    }

    @Test
    public void testGetterSetter() {
        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("u1");
        req.setSetId("s1");
        req.setExternalOrderId("e1");
        req.setExt(java.util.Map.of("k", "v"));
        assertThat(req.getUserid()).isEqualTo("u1");
        assertThat(req.getSetId()).isEqualTo("s1");
        assertThat(req.getExternalOrderId()).isEqualTo("e1");
        assertThat(req.getExt()).isNotNull();
    }
}
