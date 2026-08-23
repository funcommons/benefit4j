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
    @Bean
    @ConditionalOnMissingBean
    public org.springframework.web.client.RestTemplate benefit4jRestTemplate() {
        return new org.springframework.web.client.RestTemplate();
    }

    @Bean
    @ConditionalOnMissingBean(fun.commons.benefit4j.transport.HttpTransport.class)
    public fun.commons.benefit4j.transport.HttpTransport benefit4jHttpTransport(org.springframework.web.client.RestTemplate restTemplate) {
        return new fun.commons.benefit4j.transport.RestTemplateHttpTransport(restTemplate);
    }

    // remote 模式配了 remote-app-id → 用 AuthenticatedHttpTransport (自动 S2S JWT + HMAC 签名)
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "benefit4j.runtime", name = "remote-app-id")
    @ConditionalOnMissingBean(fun.commons.benefit4j.transport.HttpTransport.class)
    public fun.commons.benefit4j.transport.HttpTransport benefit4jAuthenticatedHttpTransport(
            fun.commons.benefit4j.transport.RestTemplateHttpTransport restTemplateTransport,
            org.springframework.beans.factory.ObjectProvider<fun.commons.framework4j.accesstoken.core.AccessTokenGenerator> tokenGeneratorProvider,
            Benefit4jProperties properties,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new fun.commons.benefit4j.transport.AuthenticatedHttpTransport(
                restTemplateTransport, tokenGeneratorProvider, properties, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.runtime", name = "mode", havingValue = "remote")
    public BenefitRuntimeClient remoteBenefitRuntimeClient(Benefit4jProperties properties, fun.commons.benefit4j.transport.HttpTransport transport) {
        return new RemoteBenefitRuntimeClient(properties, transport);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.tenant", name = "mode", havingValue = "remote")
    public BenefitTenantClient remoteBenefitTenantClient(Benefit4jProperties properties, fun.commons.benefit4j.transport.HttpTransport transport) {
        return new RemoteBenefitTenantClient(properties, transport);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.platform", name = "mode", havingValue = "remote")
    public BenefitPlatformClient remoteBenefitPlatformClient(Benefit4jProperties properties, fun.commons.benefit4j.transport.HttpTransport transport) {
        return new RemoteBenefitPlatformClient(properties, transport);
    }

    @Bean
    @ConditionalOnProperty(prefix = "benefit4j.ops", name = "mode", havingValue = "remote")
    public BenefitOpsClient remoteBenefitOpsClient(Benefit4jProperties properties, fun.commons.benefit4j.transport.HttpTransport transport) {
        return new RemoteBenefitOpsClient(properties, transport);
    }
}
