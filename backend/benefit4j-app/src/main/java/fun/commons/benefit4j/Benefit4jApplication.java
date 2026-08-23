package fun.commons.benefit4j;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class Benefit4jApplication {

    public static void main(String[] args) {
        SpringApplication.run(Benefit4jApplication.class, args);
    }
}
