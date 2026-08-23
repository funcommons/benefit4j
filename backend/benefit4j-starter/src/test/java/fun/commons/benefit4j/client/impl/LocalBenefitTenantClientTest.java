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

public class LocalBenefitTenantClientTest {

    @Mock
    private BenefitTenantService delegate;

    @InjectMocks
    private LocalBenefitTenantClient controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPostBenefitItems() {
        when(delegate.postBenefitItems((Long) any(), any())).thenReturn(null);
        controller.postBenefitItems((Long) null, (PostBenefitItemsRequest) null);
        verify(delegate).postBenefitItems((Long) any(), any());
    }

    @Test
    void testGetBenefitItems() {
        when(delegate.getBenefitItems((Long) any())).thenReturn(null);
        controller.getBenefitItems((Long) null);
        verify(delegate).getBenefitItems((Long) any());
    }

    @Test
    void testGetBenefitItemsItemId() {
        when(delegate.getBenefitItemsItemId((Long) any(), (String) any())).thenReturn(null);
        controller.getBenefitItemsItemId((Long) null, (String) null);
        verify(delegate).getBenefitItemsItemId((Long) any(), (String) any());
    }

    @Test
    void testPutBenefitItemsItemId() {
        when(delegate.putBenefitItemsItemId((Long) any(), (String) any(), any())).thenReturn(null);
        controller.putBenefitItemsItemId((Long) null, (String) null, (PutBenefitItemsItemIdRequest) null);
        verify(delegate).putBenefitItemsItemId((Long) any(), (String) any(), any());
    }

    @Test
    void testDeleteBenefitItemsItemId() {
        when(delegate.deleteBenefitItemsItemId((Long) any(), (String) any())).thenReturn(null);
        controller.deleteBenefitItemsItemId((Long) null, (String) null);
        verify(delegate).deleteBenefitItemsItemId((Long) any(), (String) any());
    }

    @Test
    void testGetBenefitTemplates() {
        when(delegate.getBenefitTemplates((Long) any())).thenReturn(null);
        controller.getBenefitTemplates((Long) null);
        verify(delegate).getBenefitTemplates((Long) any());
    }

    @Test
    void testPostBenefitSets() {
        when(delegate.postBenefitSets((Long) any(), any())).thenReturn(null);
        controller.postBenefitSets((Long) null, (PostBenefitSetsRequest) null);
        verify(delegate).postBenefitSets((Long) any(), any());
    }

    @Test
    void testGetBenefitSets() {
        when(delegate.getBenefitSets((Long) any())).thenReturn(null);
        controller.getBenefitSets((Long) null);
        verify(delegate).getBenefitSets((Long) any());
    }

    @Test
    void testGetBenefitSetsSetId() {
        when(delegate.getBenefitSetsSetId((Long) any(), (String) any())).thenReturn(null);
        controller.getBenefitSetsSetId((Long) null, (String) null);
        verify(delegate).getBenefitSetsSetId((Long) any(), (String) any());
    }

    @Test
    void testPutBenefitSetsSetId() {
        when(delegate.putBenefitSetsSetId((Long) any(), (String) any(), any())).thenReturn(null);
        controller.putBenefitSetsSetId((Long) null, (String) null, (PutBenefitSetsSetIdRequest) null);
        verify(delegate).putBenefitSetsSetId((Long) any(), (String) any(), any());
    }

    @Test
    void testDeleteBenefitSetsSetId() {
        when(delegate.deleteBenefitSetsSetId((Long) any(), (String) any())).thenReturn(null);
        controller.deleteBenefitSetsSetId((Long) null, (String) null);
        verify(delegate).deleteBenefitSetsSetId((Long) any(), (String) any());
    }

    @Test
    void testGetUsersUseridAssets() {
        when(delegate.getUsersUseridAssets((Long) any(), (String) any())).thenReturn(null);
        controller.getUsersUseridAssets((Long) null, (String) null);
        verify(delegate).getUsersUseridAssets((Long) any(), (String) any());
    }

    @Test
    void testGetUsersUseridConsumes() {
        when(delegate.getUsersUseridConsumes((Long) any(), (String) any())).thenReturn(null);
        controller.getUsersUseridConsumes((Long) null, (String) null);
        verify(delegate).getUsersUseridConsumes((Long) any(), (String) any());
    }

    @Test
    void testPostSubscriptions() {
        when(delegate.postSubscriptions((Long) any(), any())).thenReturn(null);
        controller.postSubscriptions((Long) null, (PostSubscriptionsRequest) null);
        verify(delegate).postSubscriptions((Long) any(), any());
    }

    @Test
    void testPostSubscriptionsSubscribeIdDisable() {
        when(delegate.postSubscriptionsSubscribeIdDisable((Long) any(), (String) any(), any())).thenReturn(null);
        controller.postSubscriptionsSubscribeIdDisable((Long) null, (String) null, (PostSubscriptionsSubscribeIdDisableRequest) null);
        verify(delegate).postSubscriptionsSubscribeIdDisable((Long) any(), (String) any(), any());
    }

    @Test
    void testPostCompensations() {
        when(delegate.postCompensations((Long) any(), any())).thenReturn(null);
        controller.postCompensations((Long) null, (PostCompensationsRequest) null);
        verify(delegate).postCompensations((Long) any(), any());
    }
}