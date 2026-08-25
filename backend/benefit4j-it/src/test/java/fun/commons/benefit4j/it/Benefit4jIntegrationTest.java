package fun.commons.benefit4j.it;

import fun.commons.benefit4j.client.BenefitPlatformClient;
import fun.commons.benefit4j.controller.BenefitPlatformController;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class Benefit4jIntegrationTest {

    @SpringBootApplication
    @ComponentScan(basePackages = "fun.commons.benefit4j")
    @MapperScan({"fun.commons.benefit4j.mapper", "fun.commons.benefit4j.assets.mapper"})
    static class TestApplication {
    }

    @Autowired
    private BenefitPlatformController platformController;
    
    @Autowired
    private BenefitPlatformClient platformClient;

    @Test
    public void contextLoads() {
        assertThat(platformController).isNotNull();
        assertThat(platformClient).isNotNull();
    }
}
