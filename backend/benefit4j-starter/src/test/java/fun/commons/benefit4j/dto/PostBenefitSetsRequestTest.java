package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.ConstraintViolation;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class PostBenefitSetsRequestTest {
    private final Validator validator;

    public PostBenefitSetsRequestTest() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    public void testValidRequest() {
        PostBenefitSetsRequest req = new PostBenefitSetsRequest();
        req.setName("VIP包月");
        req.setDuration(1);
        req.setDurationUnit("month");
        req.setTimingMode("RENEWAL");
        PostBenefitSetsRequest.BenefitSetItemRef ref = new PostBenefitSetsRequest.BenefitSetItemRef();
        ref.setItemId("item-001");
        ref.setQuota(10);
        req.setItems(List.of(ref));
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    public void testEmptyItems() {
        PostBenefitSetsRequest req = new PostBenefitSetsRequest();
        req.setName("VIP包月");
        req.setDurationUnit("month");
        req.setItems(List.of());
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    public void testDefaultValues() {
        PostBenefitSetsRequest req = new PostBenefitSetsRequest();
        assertThat(req.getTimingMode()).isEqualTo("RENEWAL");
        assertThat(req.getQuota()).isEqualTo(0);
        assertThat(req.getQuotaUnit()).isEqualTo("次");
        assertThat(req.getPriority()).isEqualTo(0);
    }
}
