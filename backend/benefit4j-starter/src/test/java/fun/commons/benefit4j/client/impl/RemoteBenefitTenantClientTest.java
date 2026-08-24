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

public class RemoteBenefitTenantClientTest {

    @Mock
    private Benefit4jProperties properties;

    @Mock
    private HttpTransport transport;

    @InjectMocks
    private RemoteBenefitTenantClient instance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transport.get(any(), any())).thenReturn("ok");
        when(transport.post(any(), any(), any())).thenReturn("ok");
        when(transport.put(any(), any(), any())).thenReturn("ok");
        when(transport.delete(any(), any())).thenReturn("ok");
    }

    @Test
    void testPostBenefitItems() {
        Object result = instance.postBenefitItems((Long) null, (fun.commons.benefit4j.dto.PostBenefitItemsRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetBenefitItems() {
        Object result = instance.getBenefitItems((Long) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetBenefitItemsItemId() {
        Object result = instance.getBenefitItemsItemId((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPutBenefitItemsItemId() {
        Object result = instance.putBenefitItemsItemId((Long) null, (String) null, (fun.commons.benefit4j.dto.PutBenefitItemsItemIdRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testDeleteBenefitItemsItemId() {
        Object result = instance.deleteBenefitItemsItemId((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetBenefitTemplates() {
        Object result = instance.getBenefitTemplates((Long) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostBenefitSets() {
        Object result = instance.postBenefitSets((Long) null, (fun.commons.benefit4j.dto.PostBenefitSetsRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetBenefitSets() {
        Object result = instance.getBenefitSets((Long) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetBenefitSetsSetId() {
        Object result = instance.getBenefitSetsSetId((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPutBenefitSetsSetId() {
        Object result = instance.putBenefitSetsSetId((Long) null, (String) null, (fun.commons.benefit4j.dto.PutBenefitSetsSetIdRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testDeleteBenefitSetsSetId() {
        Object result = instance.deleteBenefitSetsSetId((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetUsersUseridAssets() {
        Object result = instance.getUsersUseridAssets((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetUsersUseridConsumes() {
        Object result = instance.getUsersUseridConsumes((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostSubscriptions() {
        Object result = instance.postSubscriptions((Long) null, (fun.commons.benefit4j.dto.PostSubscriptionsRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostSubscriptionsSubscribeIdDisable() {
        Object result = instance.postSubscriptionsSubscribeIdDisable((Long) null, (String) null, (fun.commons.benefit4j.dto.PostSubscriptionsSubscribeIdDisableRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostCompensations() {
        Object result = instance.postCompensations((Long) null, (fun.commons.benefit4j.dto.PostCompensationsRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testInit() {
        when(properties.getRemoteUrl()).thenReturn("http://localhost");
        instance.init();
    }
}
