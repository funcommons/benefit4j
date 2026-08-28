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

public class BenefitPlatformControllerTest {

    @Mock
    private BenefitPlatformClient client;

    @InjectMocks
    private BenefitPlatformController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPostTenants() {
        when(client.postTenants(any())).thenReturn(null);
        controller.postTenants((PostTenantsRequest) null);
        verify(client).postTenants(any());
    }

    @Test
    void testGetTenants() {
        when(client.getTenants()).thenReturn(null);
        controller.getTenants();
        verify(client).getTenants();
    }

    @Test
    void testPutTenantsTenantId() {
        when(client.putTenantsTenantId((Long) any(), any())).thenReturn(null);
        controller.putTenantsTenantId((Long) null, (PutTenantsTenantIdRequest) null);
        verify(client).putTenantsTenantId((Long) any(), any());
    }

    @Test
    void testPostTenantsTenantIdSecret() {
        when(client.postTenantsTenantIdSecret((Long) any())).thenReturn(null);
        controller.postTenantsTenantIdSecret((Long) null);
        verify(client).postTenantsTenantIdSecret((Long) any());
    }

    @Test
    void testPostGlobalTemplates() {
        when(client.postGlobalTemplates(any())).thenReturn(null);
        controller.postGlobalTemplates((PostGlobalTemplatesRequest) null);
        verify(client).postGlobalTemplates(any());
    }

    @Test
    void testGetGlobalTemplates() {
        when(client.getGlobalTemplates()).thenReturn(null);
        controller.getGlobalTemplates();
        verify(client).getGlobalTemplates();
    }

    @Test
    void testPutGlobalTemplatesTmplId() {
        when(client.putGlobalTemplatesTmplId((Long) any(), any())).thenReturn(null);
        controller.putGlobalTemplatesTmplId((Long) null, (PutGlobalTemplatesTmplIdRequest) null);
        verify(client).putGlobalTemplatesTmplId((Long) any(), any());
    }

    @Test
    void testDeleteGlobalTemplatesTmplId() {
        when(client.deleteGlobalTemplatesTmplId((Long) any())).thenReturn(null);
        controller.deleteGlobalTemplatesTmplId((Long) null);
        verify(client).deleteGlobalTemplatesTmplId((Long) any());
    }

    @Test
    void testGetStatisticsLiabilities() {
        when(client.getStatisticsLiabilities()).thenReturn(null);
        controller.getStatisticsLiabilities();
        verify(client).getStatisticsLiabilities();
    }

    @Test
    void testGetPlatformItems() {
        when(client.getPlatformItems((Long) any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        controller.getPlatformItems(1L, "ACTIVE", "免邮", null, null, null, null);
        verify(client).getPlatformItems(1L, "ACTIVE", "免邮", null, null, null, null);
    }

    @Test
    void testPostItemTemplates() {
        when(client.postItemTemplates(any())).thenReturn(null);
        controller.postItemTemplates((PostItemTemplatesRequest) null);
        verify(client).postItemTemplates(any());
    }

    @Test
    void testGetItemTemplates() {
        when(client.getItemTemplates()).thenReturn(null);
        controller.getItemTemplates();
        verify(client).getItemTemplates();
    }

    @Test
    void testPutItemTemplatesItemId() {
        when(client.putItemTemplatesItemId((Long) any(), any())).thenReturn(null);
        controller.putItemTemplatesItemId((Long) null, (PutItemTemplatesItemIdRequest) null);
        verify(client).putItemTemplatesItemId((Long) any(), any());
    }

    @Test
    void testDeleteItemTemplatesItemId() {
        when(client.deleteItemTemplatesItemId((Long) any())).thenReturn(null);
        controller.deleteItemTemplatesItemId((Long) null);
        verify(client).deleteItemTemplatesItemId((Long) any());
    }
}