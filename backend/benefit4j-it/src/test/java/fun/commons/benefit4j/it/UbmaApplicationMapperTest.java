package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaApplication;
import fun.commons.benefit4j.mapper.UbmaApplicationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class UbmaApplicationMapperTest {

    @Autowired
    private UbmaApplicationMapper mapper;

    @Test
    public void testInsertAndSelect() {
        String appSuffix = UUID.randomUUID().toString().substring(0, 8);
        UbmaApplication app = new UbmaApplication();
        app.setName("Integration Test App");
        app.setAppSecret("secret123");
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());

        int result = mapper.insert(app);
        assertThat(result).isEqualTo(1);
        assertThat(app.getId()).isNotNull();

        UbmaApplication dbApp = mapper.selectById(app.getId());
        assertThat(dbApp).isNotNull();
        assertThat(dbApp.getName()).isEqualTo("Integration Test App");
    }
}
