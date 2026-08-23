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

public class LocalBenefitRuntimeClientTest {

    @Mock
    private BenefitRuntimeService delegate;

    @InjectMocks
    private LocalBenefitRuntimeClient controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testPostSubscriptions() {
        when(delegate.postSubscriptions((Long) any(), any())).thenReturn(null);
        controller.postSubscriptions((Long) null, (PostSubscriptionsRequest) null);
        verify(delegate).postSubscriptions((Long) any(), any());
    }

    @Test
    void testPostSubscriptionsCancel() {
        when(delegate.postSubscriptionsCancel((Long) any(), any())).thenReturn(null);
        controller.postSubscriptionsCancel((Long) null, (PostSubscriptionsCancelRequest) null);
        verify(delegate).postSubscriptionsCancel((Long) any(), any());
    }

    @Test
    void testGetSubscriptionsSubscribeId() {
        when(delegate.getSubscriptionsSubscribeId((Long) any(), (String) any())).thenReturn(null);
        controller.getSubscriptionsSubscribeId((Long) null, (String) null);
        verify(delegate).getSubscriptionsSubscribeId((Long) any(), (String) any());
    }

    @Test
    void testPostConsumesDirect() {
        when(delegate.postConsumesDirect((Long) any(), any())).thenReturn(null);
        controller.postConsumesDirect((Long) null, (PostConsumesDirectRequest) null);
        verify(delegate).postConsumesDirect((Long) any(), any());
    }

    @Test
    void testPostConsumesReserve() {
        when(delegate.postConsumesReserve((Long) any(), any())).thenReturn(null);
        controller.postConsumesReserve((Long) null, (PostConsumesReserveRequest) null);
        verify(delegate).postConsumesReserve((Long) any(), any());
    }

    @Test
    void testPostConsumesCommit() {
        when(delegate.postConsumesCommit((Long) any(), any())).thenReturn(null);
        controller.postConsumesCommit((Long) null, (PostConsumesCommitRequest) null);
        verify(delegate).postConsumesCommit((Long) any(), any());
    }

    @Test
    void testPostConsumesRelease() {
        when(delegate.postConsumesRelease((Long) any(), any())).thenReturn(null);
        controller.postConsumesRelease((Long) null, (PostConsumesReleaseRequest) null);
        verify(delegate).postConsumesRelease((Long) any(), any());
    }

    @Test
    void testPostRefunds() {
        when(delegate.postRefunds((Long) any(), any())).thenReturn(null);
        controller.postRefunds((Long) null, (PostRefundsRequest) null);
        verify(delegate).postRefunds((Long) any(), any());
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
}