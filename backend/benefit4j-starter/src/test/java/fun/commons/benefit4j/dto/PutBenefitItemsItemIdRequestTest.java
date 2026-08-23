package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PutBenefitItemsItemIdRequestTest {
    @Test
    public void testGetterSetter() {
        PutBenefitItemsItemIdRequest req = new PutBenefitItemsItemIdRequest();
        req.setName("Updated Item");
        req.setDefaultDeduction(2);
        req.setStatus("INACTIVE");
        assertThat(req.getName()).isEqualTo("Updated Item");
        assertThat(req.getDefaultDeduction()).isEqualTo(2);
    }
}
