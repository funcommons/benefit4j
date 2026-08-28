package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PutTenantsTenantIdRequestTest {
    @Test
    public void testGetterSetter() {
        PutTenantsTenantIdRequest req = new PutTenantsTenantIdRequest();
        req.setName("Updated App");
        req.setStatus("INACTIVE");
        assertThat(req.getName()).isEqualTo("Updated App");
        assertThat(req.getStatus()).isEqualTo("INACTIVE");
    }
}
