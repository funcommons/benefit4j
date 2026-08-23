package fun.commons.benefit4j;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import fun.commons.benefit4j.autoconfigure.Benefit4jAutoConfiguration;
import fun.commons.benefit4j.client.impl.RemoteBenefitTenantClient;
import fun.commons.benefit4j.client.impl.RemoteBenefitRuntimeClient;
import fun.commons.benefit4j.client.impl.RemoteBenefitPlatformClient;
import fun.commons.benefit4j.client.impl.RemoteBenefitOpsClient;
import fun.commons.benefit4j.client.impl.LocalBenefitTenantClient;
import fun.commons.benefit4j.client.impl.LocalBenefitRuntimeClient;
import fun.commons.benefit4j.client.impl.LocalBenefitPlatformClient;
import fun.commons.benefit4j.client.impl.LocalBenefitOpsClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class Benefit4jAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Benefit4jAutoConfiguration.class))
            .withBean(fun.commons.benefit4j.service.BenefitTenantService.class, () -> mock(fun.commons.benefit4j.service.BenefitTenantService.class))
            .withBean(fun.commons.benefit4j.service.BenefitOpsService.class, () -> mock(fun.commons.benefit4j.service.BenefitOpsService.class))
            .withBean(fun.commons.benefit4j.service.BenefitRuntimeService.class, () -> mock(fun.commons.benefit4j.service.BenefitRuntimeService.class))
            .withBean(fun.commons.benefit4j.service.BenefitPlatformService.class, () -> mock(fun.commons.benefit4j.service.BenefitPlatformService.class))
            .withBean(org.springframework.jdbc.core.JdbcTemplate.class, () -> mock(org.springframework.jdbc.core.JdbcTemplate.class));

    @Test
    void testLocalMode() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitTenantClient.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitOpsClient.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitRuntimeClient.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitPlatformClient.class);
            assertThat(context).getBean(fun.commons.benefit4j.client.BenefitTenantClient.class).isInstanceOf(LocalBenefitTenantClient.class);
        });
    }

    @Test
    void testRemoteMode() {
        contextRunner.withPropertyValues(
                "benefit4j.runtime.mode=remote",
                "benefit4j.tenant.mode=remote",
                "benefit4j.platform.mode=remote",
                "benefit4j.ops.mode=remote",
                "benefit4j.runtime.remote-url=http://localhost")
            .run(context -> {
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitTenantClient.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitOpsClient.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitRuntimeClient.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.client.BenefitPlatformClient.class);
            assertThat(context).getBean(fun.commons.benefit4j.client.BenefitTenantClient.class).isInstanceOf(RemoteBenefitTenantClient.class);
        });
    }

    @Test
    void testEnableApi() {
        contextRunner.withPropertyValues(
                "benefit4j.runtime.enable-api=true",
                "benefit4j.tenant.enable-api=true",
                "benefit4j.platform.enable-api=true",
                "benefit4j.ops.enable-api=true")
            .run(context -> {
            assertThat(context).hasSingleBean(fun.commons.benefit4j.controller.BenefitTenantController.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.controller.BenefitOpsController.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.controller.BenefitRuntimeController.class);
            assertThat(context).hasSingleBean(fun.commons.benefit4j.controller.BenefitPlatformController.class);
        });
    }
}
