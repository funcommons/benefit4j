package fun.commons.benefit4j.client.impl;

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

public class LocalBenefitOpsClientTest {

    @Mock
    private BenefitOpsService delegate;

    @InjectMocks
    private LocalBenefitOpsClient controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetHealth() {
        when(delegate.getHealth()).thenReturn(null);
        controller.getHealth();
        verify(delegate).getHealth();
    }

    @Test
    void testGetMetrics() {
        when(delegate.getMetrics()).thenReturn(null);
        controller.getMetrics();
        verify(delegate).getMetrics();
    }

    @Test
    void testPostCacheEvict() {
        when(delegate.postCacheEvict(any())).thenReturn(null);
        controller.postCacheEvict((PostCacheEvictRequest) null);
        verify(delegate).postCacheEvict(any());
    }

    @Test
    void testPostJobsRefreshCycles() {
        when(delegate.postJobsRefreshCycles(any())).thenReturn(null);
        controller.postJobsRefreshCycles((PostJobsRefreshCyclesRequest) null);
        verify(delegate).postJobsRefreshCycles(any());
    }
}