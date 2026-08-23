package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PostJobsRefreshCyclesRequestTest {
    @Test
    public void testDefaultValues() {
        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        assertThat(req.getDryRun()).isEqualTo(false);
    }

    @Test
    public void testGetterSetter() {
        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        req.setAppId(12345L);
        req.setDryRun(true);
        assertThat(req.getAppId()).isEqualTo(12345L);
        assertThat(req.getDryRun()).isTrue();
    }
}