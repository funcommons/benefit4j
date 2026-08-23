package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PutBenefitSetsSetIdRequestTest {
    @Test
    public void testGetterSetter() {
        PutBenefitSetsSetIdRequest req = new PutBenefitSetsSetIdRequest();
        req.setName("Updated Set");
        req.setQuota(100);
        req.setPriority(5);
        assertThat(req.getName()).isEqualTo("Updated Set");
        assertThat(req.getQuota()).isEqualTo(100);
    }
}
