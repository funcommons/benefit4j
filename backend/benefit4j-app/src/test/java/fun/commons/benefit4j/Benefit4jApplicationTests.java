package fun.commons.benefit4j;

import fun.commons.benefit4j.client.BenefitRuntimeClient;
import fun.commons.benefit4j.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import fun.commons.framework4j.redis.manager.MultiRedisManager;
import fun.commons.framework4j.datasource.manager.MultiDataSourceManager;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.Disabled("依赖 Redis 上下文，框架4j ID 策略需要 stringRedisTemplate bean。本测试需要后续启用本地 Redis (例如 Testcontainers) 才能跑通。")
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
    "benefit4j.runtime.mode=local",
    "benefit4j.runtime.enable-api=true",
    "benefit4j.tenant.mode=local",
    "benefit4j.platform.mode=local",
    "benefit4j.ops.mode=local",
    "framework4j.redis.enabled=false",
    "framework4j.datasource.enabled=false",
    "framework4j.access-token.enabled=true",
    "framework4j.access-token.secret-key=test_secret_key_at_least_32_chars_long",
    "framework4j.access-token.hash-salt=salt",
    "framework4j.signature.enabled=false",
    "framework4j.sensitive.enabled=false",
    "framework4j.idempotency.enabled=false",
    "framework4j.cache.enabled=false",
    "framework4j.audit.enabled=false"
})
class Benefit4jApplicationTests {

    @MockBean
    private MultiRedisManager multiRedisManager;

    @MockBean
    private MultiDataSourceManager multiDataSourceManager;

    @MockBean private UbmaSubscribeMapper subscribeMapper;
    @MockBean private UbmaSubscribeItemMapper subscribeItemMapper;
    @MockBean private UbmaBenefitSetMapper benefitSetMapper;
    @MockBean private UbmaBenefitRefMapper benefitRefMapper;
    @MockBean private UbmaConsumeMapper consumeMapper;
    @MockBean private UbmaRefundMapper refundMapper;
    @MockBean private UbmaUnsubscribeMapper unsubscribeMapper;
    @MockBean private UbmaApplicationMapper applicationMapper;
    @MockBean private UbmaBenefitItemMapper benefitItemMapper;
    @MockBean private UbmaCompensationMapper compensationMapper;
    @MockBean private UbmpBenefitTmplSetMapper benefitTmplSetMapper;
    @MockBean private UbmpBenefitTmplRefMapper benefitTmplRefMapper;
    @MockBean private UbmpBenefitTmplItemMapper benefitTmplItemMapper;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BenefitRuntimeClient runtimeClient;

    @Test
    void contextLoads() {
        // Assert context successfully boots with our AutoConfiguration
        assertThat(context).isNotNull();
        assertThat(runtimeClient).isNotNull();
    }
    
    @Test
    void testLocalClientRouting() {
        // Since we are in local mode, the default service impl should be wired
        Object response = runtimeClient.getUsersUseridAssets(1L, "user-123");
        assertThat(response).isNotNull();
    }
}
