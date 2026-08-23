package fun.commons.benefit4j.controller;

import fun.commons.framework4j.accesstoken.context.TokenContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.junit.jupiter.api.BeforeEach;

import java.util.Map;

import static org.mockito.Mockito.*;
import fun.commons.benefit4j.client.*;

public class BenefitTenantControllerTest {

    @Mock
    private BenefitTenantClient client;

    @InjectMocks
    private BenefitTenantController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TokenContext.set("APP", Map.of("app_id", 1L));
    }

    @AfterEach
    void tearDown() {
        TokenContext.clear();
    }

    @Test
    void testPostBenefitItems() {
        when(client.postBenefitItems((Long) any(), any())).thenReturn(null);
        controller.postBenefitItems(null);
        verify(client).postBenefitItems((Long) any(), any());
    }

    @Test
    void testGetBenefitItems() {
        when(client.getBenefitItems((Long) any())).thenReturn(null);
        controller.getBenefitItems();
        verify(client).getBenefitItems((Long) any());
    }

    @Test
    void testGetBenefitItemsItemId() {
        when(client.getBenefitItemsItemId((Long) any(), (String) any())).thenReturn(null);
        controller.getBenefitItemsItemId(null);
        verify(client).getBenefitItemsItemId((Long) any(), (String) any());
    }

    @Test
    void testPutBenefitItemsItemId() {
        when(client.putBenefitItemsItemId((Long) any(), (String) any(), any())).thenReturn(null);
        controller.putBenefitItemsItemId(null, null);
        verify(client).putBenefitItemsItemId((Long) any(), (String) any(), any());
    }

    @Test
    void testDeleteBenefitItemsItemId() {
        when(client.deleteBenefitItemsItemId((Long) any(), (String) any())).thenReturn(null);
        controller.deleteBenefitItemsItemId(null);
        verify(client).deleteBenefitItemsItemId((Long) any(), (String) any());
    }

    @Test
    void testGetBenefitTemplates() {
        when(client.getBenefitTemplates((Long) any())).thenReturn(null);
        controller.getBenefitTemplates();
        verify(client).getBenefitTemplates((Long) any());
    }

    @Test
    void testPostBenefitSets() {
        when(client.postBenefitSets((Long) any(), any())).thenReturn(null);
        controller.postBenefitSets(null);
        verify(client).postBenefitSets((Long) any(), any());
    }

    @Test
    void testGetBenefitSets() {
        when(client.getBenefitSets((Long) any())).thenReturn(null);
        controller.getBenefitSets();
        verify(client).getBenefitSets((Long) any());
    }

    @Test
    void testGetBenefitSetsSetId() {
        when(client.getBenefitSetsSetId((Long) any(), (String) any())).thenReturn(null);
        controller.getBenefitSetsSetId(null);
        verify(client).getBenefitSetsSetId((Long) any(), (String) any());
    }

    @Test
    void testPutBenefitSetsSetId() {
        when(client.putBenefitSetsSetId((Long) any(), (String) any(), any())).thenReturn(null);
        controller.putBenefitSetsSetId(null, null);
        verify(client).putBenefitSetsSetId((Long) any(), (String) any(), any());
    }

    @Test
    void testDeleteBenefitSetsSetId() {
        when(client.deleteBenefitSetsSetId((Long) any(), (String) any())).thenReturn(null);
        controller.deleteBenefitSetsSetId(null);
        verify(client).deleteBenefitSetsSetId((Long) any(), (String) any());
    }

    @Test
    void testGetUsersUseridAssets() {
        when(client.getUsersUseridAssets((Long) any(), (String) any())).thenReturn(null);
        controller.getUsersUseridAssets(null);
        verify(client).getUsersUseridAssets((Long) any(), (String) any());
    }

    @Test
    void testGetUsersUseridConsumes() {
        when(client.getUsersUseridConsumes((Long) any(), (String) any())).thenReturn(null);
        controller.getUsersUseridConsumes(null);
        verify(client).getUsersUseridConsumes((Long) any(), (String) any());
    }

    @Test
    void testPostSubscriptions() {
        when(client.postSubscriptions((Long) any(), any())).thenReturn(null);
        controller.postSubscriptions(null);
        verify(client).postSubscriptions((Long) any(), any());
    }

    @Test
    void testPostSubscriptionsSubscribeIdDisable() {
        when(client.postSubscriptionsSubscribeIdDisable((Long) any(), (String) any(), any())).thenReturn(null);
        controller.postSubscriptionsSubscribeIdDisable(null, null);
        verify(client).postSubscriptionsSubscribeIdDisable((Long) any(), (String) any(), any());
    }

    @Test
    void testPostCompensations() {
        when(client.postCompensations((Long) any(), any())).thenReturn(null);
        controller.postCompensations(null);
        verify(client).postCompensations((Long) any(), any());
    }
}