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

public class BenefitRuntimeControllerTest {

    @Mock
    private BenefitRuntimeClient client;

    @InjectMocks
    private BenefitRuntimeController controller;

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
    void testPostSubscriptions() {
        when(client.postSubscriptions((Long) any(), any())).thenReturn(null);
        controller.postSubscriptions(null);
        verify(client).postSubscriptions((Long) any(), any());
    }

    @Test
    void testPostSubscriptionsCancel() {
        when(client.postSubscriptionsCancel((Long) any(), any())).thenReturn(null);
        controller.postSubscriptionsCancel(null);
        verify(client).postSubscriptionsCancel((Long) any(), any());
    }

    @Test
    void testGetSubscriptionsSubscribeId() {
        when(client.getSubscriptionsSubscribeId((Long) any(), (String) any())).thenReturn(null);
        controller.getSubscriptionsSubscribeId(null);
        verify(client).getSubscriptionsSubscribeId((Long) any(), (String) any());
    }

    @Test
    void testPostConsumesDirect() {
        when(client.postConsumesDirect((Long) any(), any())).thenReturn(null);
        controller.postConsumesDirect(null);
        verify(client).postConsumesDirect((Long) any(), any());
    }

    @Test
    void testPostConsumesReserve() {
        when(client.postConsumesReserve((Long) any(), any())).thenReturn(null);
        controller.postConsumesReserve(null);
        verify(client).postConsumesReserve((Long) any(), any());
    }

    @Test
    void testPostConsumesCommit() {
        when(client.postConsumesCommit((Long) any(), any())).thenReturn(null);
        controller.postConsumesCommit(null);
        verify(client).postConsumesCommit((Long) any(), any());
    }

    @Test
    void testPostConsumesRelease() {
        when(client.postConsumesRelease((Long) any(), any())).thenReturn(null);
        controller.postConsumesRelease(null);
        verify(client).postConsumesRelease((Long) any(), any());
    }

    @Test
    void testPostRefunds() {
        when(client.postRefunds((Long) any(), any())).thenReturn(null);
        controller.postRefunds(null);
        verify(client).postRefunds((Long) any(), any());
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
}