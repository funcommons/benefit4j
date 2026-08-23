package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PutGlobalTemplatesTmplIdRequestTest {
    @Test
    public void testGetterSetter() {
        PutGlobalTemplatesTmplIdRequest req = new PutGlobalTemplatesTmplIdRequest();
        req.setName("Updated Template");
        req.setDuration(2);
        req.setDurationUnit("year");
        assertThat(req.getName()).isEqualTo("Updated Template");
        assertThat(req.getDuration()).isEqualTo(2);
    }
}
