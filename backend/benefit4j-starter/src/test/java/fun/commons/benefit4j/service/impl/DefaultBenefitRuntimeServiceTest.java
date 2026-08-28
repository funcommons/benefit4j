package fun.commons.benefit4j.service.impl;

import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.framework4j.web.ApiResponse;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DefaultBenefitRuntimeServiceTest {

    @InjectMocks
    private DefaultBenefitRuntimeService service;

    @Mock private UbmaSubscribeMapper subscribeMapper;
    @Mock private UbmaSubscribeItemMapper subscribeItemMapper;
    @Mock private UbmaBenefitSetMapper benefitSetMapper;
    @Mock private UbmaBenefitRefMapper benefitRefMapper;
    @Mock private UbmaConsumeMapper consumeMapper;
    @Mock private UbmaTenantMapper applicationMapper;
    @Mock private UbmaRefundMapper refundMapper;
    @Mock private UbmaUnsubscribeMapper unsubscribeMapper;
    @Mock private fun.commons.benefit4j.mapper.UbmaOutboxMapper outboxMapper;
    @Mock private Benefit4jProperties properties;

    @BeforeAll
    static void initMpCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, UbmaSubscribe.class);
        TableInfoHelper.initTableInfo(assistant, UbmaSubscribeItem.class);
        TableInfoHelper.initTableInfo(assistant, UbmaConsume.class);
        TableInfoHelper.initTableInfo(assistant, UbmaRefund.class);
        TableInfoHelper.initTableInfo(assistant, UbmaUnsubscribe.class);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(properties.getReserveTimeoutSeconds()).thenReturn(10);
    }

    // === Subscribe ===

    @Test
    void testPostSubscriptions_SetNotFound() {
        when(benefitSetMapper.selectById(anyLong())).thenReturn(null);

        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("user-001");
        req.setSetId("999");
        req.setExternalOrderId("ext-001");

        Object result = service.postSubscriptions(1L, req);
        ApiResponse<?> resp = (ApiResponse<?>) result;
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPostSubscriptions_Success() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setTenantId(1L);
        set.setStatus("ACTIVE");
        set.setDuration(1);
        set.setDurationUnit("month");
        set.setQuota(30);
        set.setRefreshCycle(1);
        set.setRefreshCycleUnit("day");
        when(benefitSetMapper.selectById(10L)).thenReturn(set);

        UbmaBenefitRef ref = new UbmaBenefitRef();
        ref.setItemId(100L);
        ref.setQuota(10);
        ref.setRefreshCycle(1);
        ref.setRefreshCycleUnit("day");
        when(benefitRefMapper.selectList(any())).thenReturn(List.of(ref));

        when(subscribeMapper.selectOne(any())).thenReturn(null);
        doReturn(1).when(subscribeMapper).insert(any(UbmaSubscribe.class));
        doReturn(1).when(subscribeItemMapper).insert(any(UbmaSubscribeItem.class));

        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("user-001");
        req.setSetId("10");
        req.setExternalOrderId("ext-sub-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptions(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper).insert(any(UbmaSubscribe.class));
        verify(subscribeItemMapper).insert(any(UbmaSubscribeItem.class));
    }

    // === TCC Reserve ===

    @Test
    void testReserve_NoActiveSubscription() {
        when(subscribeMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(consumeMapper.selectList(any())).thenReturn(Collections.emptyList());

        PostConsumesReserveRequest req = new PostConsumesReserveRequest();
        req.setUserid("user-001");
        req.setItemId("100");
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesReserve(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testReserve_IdempotentReturn() {
        UbmaConsume existing = new UbmaConsume();
        existing.setId(500L);
        existing.setStatus("RESERVED");
        existing.setSubsItemId(200L);
        when(consumeMapper.selectList(any())).thenReturn(List.of(existing));

        PostConsumesReserveRequest req = new PostConsumesReserveRequest();
        req.setUserid("user-001");
        req.setItemId("100");
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesReserve(1L, req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testReserve_Success() {
        when(consumeMapper.selectList(any())).thenReturn(Collections.emptyList());

        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setUserid("user-001");
        sub.setSetId(10L);
        sub.setStatus("ACTIVE");
        when(subscribeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(sub)));

        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setPriority(5);
        when(benefitSetMapper.selectById(10L)).thenReturn(set);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setSubscribeId(1L);
        item.setItemId(100L);
        item.setQuotaLimit(10);
        item.setPeriodConsumed(0);
        item.setFrozenConsumed(0);
        item.setVersion(0);
        when(subscribeItemMapper.selectList(any())).thenReturn(List.of(item));
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribe freshSub = new UbmaSubscribe();
        freshSub.setId(1L);
        freshSub.setVersion(0);
        freshSub.setFrozenConsumed(0);
        when(subscribeMapper.selectById(1L)).thenReturn(freshSub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        doReturn(1).when(consumeMapper).insert(any(UbmaConsume.class));

        PostConsumesReserveRequest req = new PostConsumesReserveRequest();
        req.setUserid("user-001");
        req.setItemId("100");
        req.setExternalOrderId("ext-reserve-001");
        req.setConsumeNum(2);

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesReserve(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(consumeMapper).insert(any(UbmaConsume.class));
    }

    // === TCC Commit ===

    @Test
    void testCommit_ReserveNotFound() {
        when(consumeMapper.selectList(any())).thenReturn(Collections.emptyList());

        PostConsumesCommitRequest req = new PostConsumesCommitRequest();
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesCommit(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCommit_AlreadyCommitted() {
        UbmaConsume consume = new UbmaConsume();
        consume.setId(500L);
        consume.setStatus("COMMITTED");
        consume.setSubsItemId(200L);
        consume.setExternalOrderId("ext-reserve-001");
        when(consumeMapper.selectList(any())).thenReturn(List.of(consume));

        PostConsumesCommitRequest req = new PostConsumesCommitRequest();
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesCommit(1L, req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testCommit_Success() {
        UbmaConsume consume = new UbmaConsume();
        consume.setId(500L);
        consume.setTenantId(1L);
        consume.setStatus("RESERVED");
        consume.setSubsItemId(200L);
        consume.setItemId(100L);
        consume.setConsumeNum(2);
        consume.setExternalOrderId("ext-reserve-001");
        consume.setExpireTime(OffsetDateTime.now().plusSeconds(30));
        when(consumeMapper.selectList(any())).thenReturn(List.of(consume));
        when(consumeMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setTenantId(1L);
        item.setSubscribeId(1L);
        item.setFrozenConsumed(2);
        item.setPeriodConsumed(0);
        item.setTotalConsumed(0);
        item.setVersion(1);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setFrozenConsumed(2);
        sub.setPeriodConsumed(0);
        sub.setTotalConsumed(0);
        sub.setVersion(1);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        PostConsumesCommitRequest req = new PostConsumesCommitRequest();
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesCommit(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeItemMapper).update(any(), any());
        verify(subscribeMapper).update(any(), any());
    }

    // === TCC Release ===

    @Test
    void testRelease_ReserveNotFound() {
        when(consumeMapper.selectList(any())).thenReturn(Collections.emptyList());

        PostConsumesReleaseRequest req = new PostConsumesReleaseRequest();
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesRelease(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testRelease_Success() {
        UbmaConsume consume = new UbmaConsume();
        consume.setId(500L);
        consume.setTenantId(1L);
        consume.setStatus("RESERVED");
        consume.setSubsItemId(200L);
        consume.setConsumeNum(2);
        consume.setExternalOrderId("ext-reserve-001");
        consume.setExpireTime(OffsetDateTime.now().plusSeconds(30));
        when(consumeMapper.selectList(any())).thenReturn(List.of(consume));
        when(consumeMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setTenantId(1L);
        item.setSubscribeId(1L);
        item.setFrozenConsumed(2);
        item.setPeriodConsumed(0);
        item.setTotalConsumed(0);
        item.setVersion(1);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setFrozenConsumed(2);
        sub.setVersion(1);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        PostConsumesReleaseRequest req = new PostConsumesReleaseRequest();
        req.setExternalOrderId("ext-reserve-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postConsumesRelease(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeItemMapper).update(any(), any());
        verify(subscribeMapper).update(any(), any());
    }

    // === Refund ===

    @Test
    void testRefund_ConsumeNotFound() {
        when(consumeMapper.selectById(anyLong())).thenReturn(null);

        PostRefundsRequest req = new PostRefundsRequest();
        req.setConsumeId("999");
        req.setExternalRefundId("ext-refund-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postRefunds(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testRefund_NotCommitted() {
        UbmaConsume consume = new UbmaConsume();
        consume.setId(500L);
        consume.setTenantId(1L);
        consume.setStatus("RESERVED");
        when(consumeMapper.selectById(500L)).thenReturn(consume);

        PostRefundsRequest req = new PostRefundsRequest();
        req.setConsumeId("500");
        req.setExternalRefundId("ext-refund-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postRefunds(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testRefund_IdempotentReturn() {
        when(refundMapper.selectOne(any())).thenReturn(new UbmaRefund());

        UbmaConsume consume = new UbmaConsume();
        consume.setId(500L);
        consume.setTenantId(1L);
        consume.setStatus("COMMITTED");
        when(consumeMapper.selectById(500L)).thenReturn(consume);

        PostRefundsRequest req = new PostRefundsRequest();
        req.setConsumeId("500");
        req.setExternalRefundId("ext-refund-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postRefunds(1L, req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testRefund_Success() {
        UbmaConsume consume = new UbmaConsume();
        consume.setId(500L);
        consume.setTenantId(1L);
        consume.setStatus("COMMITTED");
        consume.setSubsItemId(200L);
        consume.setItemId(100L);
        consume.setConsumeNum(2);
        when(consumeMapper.selectById(500L)).thenReturn(consume);
        when(refundMapper.selectOne(any())).thenReturn(null);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setTenantId(1L);
        item.setSubscribeId(1L);
        item.setPeriodConsumed(5);
        item.setTotalConsumed(10);
        item.setVersion(2);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setPeriodConsumed(5);
        sub.setTotalConsumed(10);
        sub.setVersion(2);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        doReturn(1).when(refundMapper).insert(any(UbmaRefund.class));

        PostRefundsRequest req = new PostRefundsRequest();
        req.setConsumeId("500");
        req.setExternalRefundId("ext-refund-001");
        req.setRefundNum(2);

        ApiResponse<?> resp = (ApiResponse<?>) service.postRefunds(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(refundMapper).insert(any(UbmaRefund.class));
        verify(subscribeItemMapper).update(any(), any());
        verify(subscribeMapper).update(any(), any());
    }

    // === Cancel ===

    @Test
    void testCancel_NotFound() {
        when(subscribeMapper.selectById(anyLong())).thenReturn(null);

        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        req.setSubscribeId("999");
        req.setExternalOrderId("ext-cancel-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptionsCancel(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCancel_FrozenQuotaRejects() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setFrozenConsumed(3);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);

        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        req.setSubscribeId("1");
        req.setExternalOrderId("ext-cancel-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptionsCancel(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCancel_Success() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setFrozenConsumed(0);
        sub.setVersion(0);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);
        doReturn(1).when(unsubscribeMapper).insert(any(UbmaUnsubscribe.class));

        PostSubscriptionsCancelRequest req = new PostSubscriptionsCancelRequest();
        req.setSubscribeId("1");
        req.setExternalOrderId("ext-cancel-001");
        req.setReason("用户主动退订");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptionsCancel(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper).update(any(), any());
        verify(unsubscribeMapper).insert(any(UbmaUnsubscribe.class));
    }

    // === Query ===

    @Test
    void testGetSubscriptions_NotFound() {
        when(subscribeMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.getSubscriptionsSubscribeId(1L, "999");
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testGetAssets_Empty() {
        when(subscribeMapper.selectList(any())).thenReturn(Collections.emptyList());
        ApiResponse<?> resp = (ApiResponse<?>) service.getUsersUseridAssets(1L, "user-001");
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetConsumes_NoSubscriptions() {
        when(subscribeMapper.selectList(any())).thenReturn(Collections.emptyList());
        ApiResponse<?> resp = (ApiResponse<?>) service.getUsersUseridConsumes(1L, "user-001");
        assertThat(resp.isSuccess()).isTrue();
    }
}

