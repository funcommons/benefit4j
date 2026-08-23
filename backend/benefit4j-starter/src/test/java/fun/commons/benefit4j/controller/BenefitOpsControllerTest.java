package fun.commons.benefit4j.controller;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import fun.commons.benefit4j.service.*;
import fun.commons.benefit4j.client.*;
import fun.commons.benefit4j.dto.*;

public class BenefitOpsControllerTest {

    @Mock
    private BenefitOpsClient client;

    @InjectMocks
    private BenefitOpsController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetHealth() {
        when(client.getHealth()).thenReturn(null);
        controller.getHealth();
        verify(client).getHealth();
    }

    @Test
    void testGetMetrics() {
        when(client.getMetrics()).thenReturn(null);
        controller.getMetrics();
        verify(client).getMetrics();
    }

    @Test
    void testPostCacheEvict() {
        when(client.postCacheEvict(any())).thenReturn(null);
        controller.postCacheEvict((PostCacheEvictRequest) null);
        verify(client).postCacheEvict(any());
    }

    @Test
    void testPostJobsRefreshCycles() {
        when(client.postJobsRefreshCycles(any())).thenReturn(null);
        controller.postJobsRefreshCycles((PostJobsRefreshCyclesRequest) null);
        verify(client).postJobsRefreshCycles(any());
    }
}