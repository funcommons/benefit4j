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

public class RemoteBenefitRuntimeClientTest {

    @Mock
    private Benefit4jProperties properties;

    @Mock
    private HttpTransport transport;

    @InjectMocks
    private RemoteBenefitRuntimeClient instance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(transport.post(any(), any(), any())).thenReturn("ok");
        when(transport.get(any(), any())).thenReturn("ok");
    }

    @Test
    void testPostSubscriptions() {
        Object result = instance.postSubscriptions((Long) null, (fun.commons.benefit4j.dto.PostSubscriptionsRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostSubscriptionsCancel() {
        Object result = instance.postSubscriptionsCancel((Long) null, (fun.commons.benefit4j.dto.PostSubscriptionsCancelRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testGetSubscriptionsSubscribeId() {
        Object result = instance.getSubscriptionsSubscribeId((Long) null, (String) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostConsumesDirect() {
        Object result = instance.postConsumesDirect((Long) null, (fun.commons.benefit4j.dto.PostConsumesDirectRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostConsumesReserve() {
        Object result = instance.postConsumesReserve((Long) null, (fun.commons.benefit4j.dto.PostConsumesReserveRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostConsumesCommit() {
        Object result = instance.postConsumesCommit((Long) null, (fun.commons.benefit4j.dto.PostConsumesCommitRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostConsumesRelease() {
        Object result = instance.postConsumesRelease((Long) null, (fun.commons.benefit4j.dto.PostConsumesReleaseRequest) null);
        assertThat(result).isNotNull();
    }

    @Test
    void testPostRefunds() {
        Object result = instance.postRefunds((Long) null, (fun.commons.benefit4j.dto.PostRefundsRequest) null);
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
    void testInit() {
        when(properties.getRemoteUrl()).thenReturn("http://localhost");
        instance.init();
    }
}
