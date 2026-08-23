package fun.commons.benefit4j;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
public class Benefit4jApplicationMainTest {
    @Test
    void testMain() {
        try (MockedStatic<SpringApplication> mocked = Mockito.mockStatic(SpringApplication.class)) {
            mocked.when(() -> SpringApplication.run(Benefit4jApplication.class, new String[]{})).thenReturn(null);
            Benefit4jApplication.main(new String[]{});
            mocked.verify(() -> SpringApplication.run(Benefit4jApplication.class, new String[]{}));
        }
    }
}
