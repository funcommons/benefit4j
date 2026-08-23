package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaApplication;
import fun.commons.benefit4j.mapper.UbmaApplicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.OffsetDateTime;
import java.util.UUID;

@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public abstract class BaseMapperTest {

    @Autowired
    protected UbmaApplicationMapper appMapper;

    protected String uniqueAppid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected Long uniqueLongId() {
        return UUID.randomUUID().getMostSignificantBits() & 0x7FFFFFFFL;
    }

    protected UbmaApplication createApp() {
        String suffix = uniqueAppid();
        UbmaApplication app = new UbmaApplication();
        app.setName("test-" + suffix);
        app.setAppSecret("secret-" + suffix);
        app.setCreatedAt(OffsetDateTime.now());
        app.setUpdatedAt(OffsetDateTime.now());
        appMapper.insert(app);
        return app;
    }
}
