package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaTenant;
import fun.commons.benefit4j.mapper.UbmaTenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public abstract class BaseMapperTest {

    @Autowired
    protected UbmaTenantMapper tenantMapper;

    protected String uniqueTenantid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected Long uniqueLongId() {
        return UUID.randomUUID().getMostSignificantBits() & 0x7FFFFFFFL;
    }

    protected UbmaTenant createTenant() {
        String suffix = uniqueTenantid();
        UbmaTenant app = new UbmaTenant();
        app.setName("test-" + suffix);
        app.setTenantSecret("secret-" + suffix);
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());
        tenantMapper.insert(app);
        return app;
    }
}
