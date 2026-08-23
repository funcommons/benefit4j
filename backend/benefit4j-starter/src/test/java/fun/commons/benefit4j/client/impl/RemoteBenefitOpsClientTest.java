package fun.commons.benefit4j.client.impl;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.benefit4j.transport.HttpTransport;
import fun.commons.benefit4j.dto.*;

public class RemoteBenefitOpsClientTest {

    @Mock
    private Benefit4jProperties properties;

    @Mock
    private HttpTransport transport;

    @InjectMocks
    private RemoteBenefitOpsClient instance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transport.post(any(), any(), any())).thenReturn("ok");
        when(transport.get(any(), any())).thenReturn("ok");
    }

    @Test
    void testGetHealth() {
        Object result = instance.getHealth();
        assertThat(result).isNotNull();
    }

    @Test
    void testGetMetrics() {
        Object result = instance.getMetrics();
        assertThat(result).isNotNull();
    }

    @Test
    void testPostCacheEvict() {
        Object result = instance.postCacheEvict((fun.commons.benefit4j.dto.PostCacheEvictRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostJobsRefreshCycles() {
        Object result = instance.postJobsRefreshCycles((fun.commons.benefit4j.dto.PostJobsRefreshCyclesRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testInit() {
        when(properties.getRemoteUrl()).thenReturn("http://localhost");
        instance.init();
    }
}
