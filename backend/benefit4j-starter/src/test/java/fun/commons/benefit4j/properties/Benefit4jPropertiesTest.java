package fun.commons.benefit4j.properties;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class Benefit4jPropertiesTest {
    @Test
    void testGettersAndSetters() {
        Benefit4jProperties obj = new Benefit4jProperties();
        obj.setMode("remote");
        assertThat(obj.getMode()).isEqualTo("remote");
        obj.setEnableApi(true);
        assertThat(obj.isEnableApi()).isTrue();
        obj.setRemoteUrl("http://localhost");
        assertThat(obj.getRemoteUrl()).isEqualTo("http://localhost");
        assertThat(obj.getReserveTimeoutSeconds()).isEqualTo(10);
        obj.setReserveTimeoutSeconds(30);
        assertThat(obj.getReserveTimeoutSeconds()).isEqualTo(30);
    }
}
