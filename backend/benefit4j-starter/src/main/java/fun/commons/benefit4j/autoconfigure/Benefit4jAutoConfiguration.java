package fun.commons.benefit4j.autoconfigure;

import fun.commons.benefit4j.boot.ReserveTimeoutScheduler;
import fun.commons.benefit4j.client.*;
import fun.commons.benefit4j.client.impl.*;
import fun.commons.benefit4j.controller.*;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.benefit4j.service.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(Benefit4jProperties.class)
@EnableScheduling
public class Benefit4jAutoConfiguration {

    // ==== Runtime ====
    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.runtime", name = "mode", havingValue = "local", matchIfMissing = true)
    public BenefitRuntimeClient localBenefitRuntimeClient(BenefitRuntimeService service) {
        return new LocalBenefitRuntimeClient(service);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.runtime", name = "mode", havingValue = "local", matchIfMissing = true)
    public ReserveTimeoutScheduler reserveTimeoutScheduler(BenefitRuntimeService service) {
        return new ReserveTimeoutScheduler(service);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.runtime", name = "mode", havingValue = "local", matchIfMissing = true)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(fun.commons.benefit4j.mapper.UbmaOutboxMapper.class)
    public fun.commons.benefit4j.boot.OutboxPublisher outboxPublisher(fun.commons.benefit4j.mapper.UbmaOutboxMapper outboxMapper) {
        return new fun.commons.benefit4j.boot.OutboxPublisher(outboxMapper);
    }
    
    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.runtime", name = "enable-api", havingValue = "true")
    public BenefitRuntimeController benefitRuntimeController(BenefitRuntimeClient client) {
        return new BenefitRuntimeController(client);
    }

    // ==== Tenant ====
    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.tenant", name = "mode", havingValue = "local", matchIfMissing = true)
    public BenefitTenantClient localBenefitTenantClient(BenefitTenantService service) {
        return new LocalBenefitTenantClient(service);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.tenant", name = "enable-api", havingValue = "true")
    public BenefitTenantController benefitTenantController(BenefitTenantClient client) {
        return new BenefitTenantController(client);
    }

    // ==== Platform ====
    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.platform", name = "mode", havingValue = "local", matchIfMissing = true)
    public BenefitPlatformClient localBenefitPlatformClient(BenefitPlatformService service) {
        return new LocalBenefitPlatformClient(service);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.platform", name = "enable-api", havingValue = "true")
    public BenefitPlatformController benefitPlatformController(BenefitPlatformClient client) {
        return new BenefitPlatformController(client);
    }

    // ==== Ops ====
    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.ops", name = "mode", havingValue = "local", matchIfMissing = true)
    public BenefitOpsClient localBenefitOpsClient(BenefitOpsService service) {
        return new LocalBenefitOpsClient(service);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.ops", name = "enable-api", havingValue = "true")
    public BenefitOpsController benefitOpsController(BenefitOpsClient client, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        return new BenefitOpsController(client, jdbc);
    }

    // ==== Remote Mode (跨进程调用, 业务方需配 benefit4j.{域}.mode=remote + remote-url) ====
    // 默认 RestTemplate + HttpTransport 由 framework4j-transport 的 TransportAutoConfiguration 提供,
    // 此处仅 remote 模式用 AuthenticatedHttpTransport 覆盖 (S2S JWT + HMAC 签名)

    // remote 模式配了 remote-app-id → 用 AuthenticatedHttpTransport 覆盖默认 (自动 S2S JWT + HMAC 签名)
    // @Primary 优先于 framework4j-transport 的默认 RestTemplateHttpTransport
    @Bean
    @org.springframework.context.annotation.Primary
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "benefit4j.runtime", name = "remote-app-id")
    public fun.commons.framework4j.transport.HttpTransport benefit4jAuthenticatedHttpTransport(
            org.springframework.web.client.RestTemplate restTemplate,
            org.springframework.beans.factory.ObjectProvider<fun.commons.framework4j.accesstoken.core.AccessTokenGenerator> tokenGeneratorProvider,
            Benefit4jProperties properties,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        fun.commons.framework4j.transport.RestTemplateHttpTransport delegate =
                new fun.commons.framework4j.transport.RestTemplateHttpTransport(restTemplate);
        return new fun.commons.benefit4j.transport.AuthenticatedHttpTransport(
                delegate, tokenGeneratorProvider, properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.runtime", name = "mode", havingValue = "remote")
    public BenefitRuntimeClient remoteBenefitRuntimeClient(Benefit4jProperties properties, fun.commons.framework4j.transport.HttpTransport transport) {
        return new RemoteBenefitRuntimeClient(properties, transport);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.tenant", name = "mode", havingValue = "remote")
    public BenefitTenantClient remoteBenefitTenantClient(Benefit4jProperties properties, fun.commons.framework4j.transport.HttpTransport transport) {
        return new RemoteBenefitTenantClient(properties, transport);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.platform", name = "mode", havingValue = "remote")
    public BenefitPlatformClient remoteBenefitPlatformClient(Benefit4jProperties properties, fun.commons.framework4j.transport.HttpTransport transport) {
        return new RemoteBenefitPlatformClient(properties, transport);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.ops", name = "mode", havingValue = "remote")
    public BenefitOpsClient remoteBenefitOpsClient(Benefit4jProperties properties, fun.commons.framework4j.transport.HttpTransport transport) {
        return new RemoteBenefitOpsClient(properties, transport);
    }
}
