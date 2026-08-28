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

public class LocalBenefitPlatformClientTest {

    @Mock
    private BenefitPlatformService delegate;

    @InjectMocks
    private LocalBenefitPlatformClient controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPostTenants() {
        when(delegate.postTenants(any())).thenReturn(null);
        controller.postTenants((PostTenantsRequest) null);
        verify(delegate).postTenants(any());
    }

    @Test
    void testGetTenants() {
        when(delegate.getTenants()).thenReturn(null);
        controller.getTenants();
        verify(delegate).getTenants();
    }

    @Test
    void testPutTenantsTenantId() {
        when(delegate.putTenantsTenantId((Long) any(), any())).thenReturn(null);
        controller.putTenantsTenantId((Long) null, (PutTenantsTenantIdRequest) null);
        verify(delegate).putTenantsTenantId((Long) any(), any());
    }

    @Test
    void testPostTenantsTenantIdSecret() {
        when(delegate.postTenantsTenantIdSecret((Long) any())).thenReturn(null);
        controller.postTenantsTenantIdSecret((Long) null);
        verify(delegate).postTenantsTenantIdSecret((Long) any());
    }

    @Test
    void testPostGlobalTemplates() {
        when(delegate.postGlobalTemplates(any())).thenReturn(null);
        controller.postGlobalTemplates((PostGlobalTemplatesRequest) null);
        verify(delegate).postGlobalTemplates(any());
    }

    @Test
    void testGetGlobalTemplates() {
        when(delegate.getGlobalTemplates()).thenReturn(null);
        controller.getGlobalTemplates();
        verify(delegate).getGlobalTemplates();
    }

    @Test
    void testPutGlobalTemplatesTmplId() {
        when(delegate.putGlobalTemplatesTmplId((Long) any(), any())).thenReturn(null);
        controller.putGlobalTemplatesTmplId((Long) null, (PutGlobalTemplatesTmplIdRequest) null);
        verify(delegate).putGlobalTemplatesTmplId((Long) any(), any());
    }

    @Test
    void testDeleteGlobalTemplatesTmplId() {
        when(delegate.deleteGlobalTemplatesTmplId((Long) any())).thenReturn(null);
        controller.deleteGlobalTemplatesTmplId((Long) null);
        verify(delegate).deleteGlobalTemplatesTmplId((Long) any());
    }

    @Test
    void testGetStatisticsLiabilities() {
        when(delegate.getStatisticsLiabilities()).thenReturn(null);
        controller.getStatisticsLiabilities();
        verify(delegate).getStatisticsLiabilities();
    }

    @Test
    void testGetPlatformItems() {
        when(delegate.getPlatformItems((Long) any(), any(), any(), any(), any(), any(), any())).thenReturn(null);
        controller.getPlatformItems(1L, "ACTIVE", "免邮", null, null, null, null);
        verify(delegate).getPlatformItems(1L, "ACTIVE", "免邮", null, null, null, null);
    }

    @Test
    void testPostItemTemplates() {
        when(delegate.postItemTemplates(any())).thenReturn(null);
        controller.postItemTemplates((PostItemTemplatesRequest) null);
        verify(delegate).postItemTemplates(any());
    }

    @Test
    void testGetItemTemplates() {
        when(delegate.getItemTemplates()).thenReturn(null);
        controller.getItemTemplates();
        verify(delegate).getItemTemplates();
    }

    @Test
    void testPutItemTemplatesItemId() {
        when(delegate.putItemTemplatesItemId((Long) any(), any())).thenReturn(null);
        controller.putItemTemplatesItemId((Long) null, (PutItemTemplatesItemIdRequest) null);
        verify(delegate).putItemTemplatesItemId((Long) any(), any());
    }

    @Test
    void testDeleteItemTemplatesItemId() {
        when(delegate.deleteItemTemplatesItemId((Long) any())).thenReturn(null);
        controller.deleteItemTemplatesItemId((Long) null);
        verify(delegate).deleteItemTemplatesItemId((Long) any());
    }
}