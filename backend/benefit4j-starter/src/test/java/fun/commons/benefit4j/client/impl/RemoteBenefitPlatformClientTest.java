package fun.commons.benefit4j.client.impl;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.framework4j.transport.HttpTransport;
import fun.commons.benefit4j.dto.*;

public class RemoteBenefitPlatformClientTest {

    @Mock
    private Benefit4jProperties properties;

    @Mock
    private HttpTransport transport;

    @InjectMocks
    private RemoteBenefitPlatformClient instance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transport.get(any(), any())).thenReturn("ok");
        when(transport.post(any(), any(), any())).thenReturn("ok");
        when(transport.put(any(), any(), any())).thenReturn("ok");
        when(transport.delete(any(), any())).thenReturn("ok");
    }

    @Test
    void testPostApplications() {
        Object result = instance.postApplications((fun.commons.benefit4j.dto.PostApplicationsRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetApplications() {
        Object result = instance.getApplications();
        assertThat(result).isNotNull();
    }

    @Test
    void testPutApplicationsAppId() {
        Object result = instance.putApplicationsAppId((Long) null, (fun.commons.benefit4j.dto.PutApplicationsAppIdRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostGlobalTemplates() {
        Object result = instance.postGlobalTemplates((fun.commons.benefit4j.dto.PostGlobalTemplatesRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetGlobalTemplates() {
        Object result = instance.getGlobalTemplates();
        assertThat(result).isNotNull();
    }

    @Test
    void testPutGlobalTemplatesTmplId() {
        Object result = instance.putGlobalTemplatesTmplId((Long) null, (fun.commons.benefit4j.dto.PutGlobalTemplatesTmplIdRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testDeleteGlobalTemplatesTmplId() {
        Object result = instance.deleteGlobalTemplatesTmplId((Long) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetStatisticsLiabilities() {
        Object result = instance.getStatisticsLiabilities();
        assertThat(result).isNotNull();
    }

    @Test
    void testGetPlatformItems() {
        Object result = instance.getPlatformItems(1L, "ACTIVE", "免邮", null, null, null, null);
        assertThat(result).isNotNull();
    }

    @Test
    void testInit() {
        when(properties.getRemoteUrl()).thenReturn("http://localhost");
        instance.init();
    }
}
