package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.framework4j.web.ApiResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DefaultBenefitTenantServiceTest {

    @InjectMocks
    private DefaultBenefitTenantService service;

    @Mock private UbmaCompensationMapper compensationMapper;
    @Mock private UbmaSubscribeMapper subscribeMapper;
    @Mock private UbmaSubscribeItemMapper subscribeItemMapper;
    @Mock private UbmaBenefitItemMapper benefitItemMapper;
    @Mock private UbmaBenefitSetMapper benefitSetMapper;
    @Mock private UbmaBenefitRefMapper benefitRefMapper;
    @Mock private UbmpBenefitTmplSetMapper benefitTmplSetMapper;
    @Mock private UbmpBenefitTmplRefMapper benefitTmplRefMapper;

    @BeforeAll
    static void initMpCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, UbmaCompensation.class);
        TableInfoHelper.initTableInfo(assistant, UbmaSubscribe.class);
        TableInfoHelper.initTableInfo(assistant, UbmaSubscribeItem.class);
        TableInfoHelper.initTableInfo(assistant, UbmaBenefitItem.class);
        TableInfoHelper.initTableInfo(assistant, UbmaBenefitSet.class);
        TableInfoHelper.initTableInfo(assistant, UbmaBenefitRef.class);
        TableInfoHelper.initTableInfo(assistant, UbmpBenefitTmplSet.class);
        TableInfoHelper.initTableInfo(assistant, UbmpBenefitTmplRef.class);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // === Compensation ===

    @Test
    void testCompensation_SubscribeNotFound() {
        when(subscribeMapper.selectById(anyLong())).thenReturn(null);

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("999");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(5);
        req.setAdjustType("ADD");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCompensation_SubItemNotFound() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeItemMapper.selectById(200L)).thenReturn(null);

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("1");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(5);
        req.setAdjustType("ADD");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCompensation_AddSuccess() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setQuotaLimit(30);
        sub.setVersion(0);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setSubscribeId(1L);
        item.setQuotaLimit(10);
        item.setVersion(0);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        doReturn(1).when(compensationMapper).insert(any(UbmaCompensation.class));

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("1");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(5);
        req.setAdjustType("ADD");
        req.setReason("客服补偿");
        req.setOperator("cs-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(compensationMapper).insert(any(UbmaCompensation.class));
        // V1.2.0 多源桶: ADD 改 insert 新桶 (而不是 update quotaLimit)
        verify(subscribeItemMapper).insert(any(UbmaSubscribeItem.class));
        verify(subscribeMapper).update(any(), any());
    }

    @Test
    void testCompensation_ReduceSuccess() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setQuotaLimit(30);
        sub.setVersion(0);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setSubscribeId(1L);
        item.setQuotaLimit(10);
        item.setVersion(0);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        doReturn(1).when(compensationMapper).insert(any(UbmaCompensation.class));

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("1");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(3);
        req.setAdjustType("REDUCE");
        req.setReason("误操作修正");
        req.setOperator("cs-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(compensationMapper).insert(any(UbmaCompensation.class));
    }

    // === Benefit Items ===

    @Test
    void testPostBenefitItems_Success() {
        when(benefitItemMapper.selectCount(any())).thenReturn(0L);
        doReturn(1).when(benefitItemMapper).insert(any(UbmaBenefitItem.class));

        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("免邮特权");
        req.setDescription("每月免邮3次");
        req.setDefaultDeduction(1);

        ApiResponse<?> resp = (ApiResponse<?>) service.postBenefitItems(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitItemMapper).insert(any(UbmaBenefitItem.class));
    }

    @Test
    void testPostBenefitItems_DuplicateNameRejects() {
        when(benefitItemMapper.selectCount(any())).thenReturn(1L);

        PostBenefitItemsRequest req = new PostBenefitItemsRequest();
        req.setName("免邮特权");
        req.setDescription("重复名称");

        ApiResponse<?> resp = (ApiResponse<?>) service.postBenefitItems(1L, req);
        assertThat(resp.isFail()).isTrue();
        verify(benefitItemMapper, never()).insert(any(UbmaBenefitItem.class));
    }

    @Test
    void testGetBenefitItems_Success() {
        when(benefitItemMapper.selectList(any())).thenReturn(java.util.List.of(new UbmaBenefitItem()));
        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitItems(1L);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetBenefitItemsItemId_Success() {
        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setId(100L);
        item.setTenantId(1L);
        when(benefitItemMapper.selectById(100L)).thenReturn(item);

        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitItemsItemId(1L, "100");
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetBenefitItemsItemId_NotFound() {
        when(benefitItemMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitItemsItemId(1L, "999");
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPutBenefitItemsItemId_Success() {
        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setId(100L);
        item.setTenantId(1L);
        when(benefitItemMapper.selectById(100L)).thenReturn(item);
        when(benefitItemMapper.update(any(), any())).thenReturn(1);

        PutBenefitItemsItemIdRequest req = new PutBenefitItemsItemIdRequest();
        req.setName("新名称");
        ApiResponse<?> resp = (ApiResponse<?>) service.putBenefitItemsItemId(1L, "100", req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testDeleteBenefitItemsItemId_Success() {
        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setId(100L);
        item.setTenantId(1L);
        when(benefitItemMapper.selectById(100L)).thenReturn(item);
        when(benefitItemMapper.deleteById(100L)).thenReturn(1);

        ApiResponse<?> resp = (ApiResponse<?>) service.deleteBenefitItemsItemId(1L, "100");
        assertThat(resp.isSuccess()).isTrue();
    }

    // === Benefit Sets ===

    @Test
    void testPostBenefitSets_Success() {
        UbmaBenefitItem refItem = new UbmaBenefitItem();
        refItem.setId(100L);
        refItem.setTenantId(1L);
        when(benefitItemMapper.selectById(100L)).thenReturn(refItem);

        doReturn(1).when(benefitSetMapper).insert(any(UbmaBenefitSet.class));
        doReturn(1).when(benefitRefMapper).insert(any(UbmaBenefitRef.class));

        PostBenefitSetsRequest req = new PostBenefitSetsRequest();
        req.setName("VIP月卡");
        req.setDuration(1);
        req.setDurationUnit("month");
        req.setQuota(30);
        PostBenefitSetsRequest.BenefitSetItemRef itemRef = new PostBenefitSetsRequest.BenefitSetItemRef();
        itemRef.setItemId("100");
        itemRef.setQuota(10);
        req.setItems(java.util.List.of(itemRef));

        ApiResponse<?> resp = (ApiResponse<?>) service.postBenefitSets(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitSetMapper).insert(any(UbmaBenefitSet.class));
        verify(benefitRefMapper).insert(any(UbmaBenefitRef.class));
    }

    @Test
    void testGetBenefitSets_Success() {
        when(benefitSetMapper.selectList(any())).thenReturn(java.util.List.of(new UbmaBenefitSet()));
        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitSets(1L);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetBenefitSetsSetId_Success() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setTenantId(1L);
        when(benefitSetMapper.selectById(10L)).thenReturn(set);
        when(benefitRefMapper.selectList(any())).thenReturn(java.util.List.of(new UbmaBenefitRef()));

        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitSetsSetId(1L, "10");
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetBenefitSetsSetId_NotFound() {
        when(benefitSetMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitSetsSetId(1L, "999");
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPutBenefitSetsSetId_Success() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setTenantId(1L);
        when(benefitSetMapper.selectById(10L)).thenReturn(set);
        when(benefitSetMapper.update(any(), any())).thenReturn(1);

        PutBenefitSetsSetIdRequest req = new PutBenefitSetsSetIdRequest();
        req.setName("新名称");
        req.setQuota(50);
        ApiResponse<?> resp = (ApiResponse<?>) service.putBenefitSetsSetId(1L, "10", req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitSetMapper).update(any(), any());
    }

    @Test
    void testPutBenefitSetsSetId_NotFound() {
        when(benefitSetMapper.selectById(anyLong())).thenReturn(null);
        PutBenefitSetsSetIdRequest req = new PutBenefitSetsSetIdRequest();
        req.setName("新名称");
        ApiResponse<?> resp = (ApiResponse<?>) service.putBenefitSetsSetId(1L, "999", req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPutBenefitSetsSetId_WithItems() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setTenantId(1L);
        when(benefitSetMapper.selectById(10L)).thenReturn(set);
        when(benefitSetMapper.update(any(), any())).thenReturn(1);

        UbmaBenefitItem refItem = new UbmaBenefitItem();
        refItem.setId(100L);
        refItem.setTenantId(1L);
        when(benefitItemMapper.selectById(100L)).thenReturn(refItem);
        when(benefitRefMapper.delete(any())).thenReturn(0);
        doReturn(1).when(benefitRefMapper).insert(any(UbmaBenefitRef.class));

        PutBenefitSetsSetIdRequest req = new PutBenefitSetsSetIdRequest();
        req.setName("新名称");
        PostBenefitSetsRequest.BenefitSetItemRef itemRef = new PostBenefitSetsRequest.BenefitSetItemRef();
        itemRef.setItemId("100");
        itemRef.setQuota(15);
        req.setItems(java.util.List.of(itemRef));

        ApiResponse<?> resp = (ApiResponse<?>) service.putBenefitSetsSetId(1L, "10", req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitRefMapper).delete(any());
        verify(benefitRefMapper).insert(any(UbmaBenefitRef.class));
    }

    @Test
    void testDeleteBenefitSetsSetId_Success() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setTenantId(1L);
        when(benefitSetMapper.selectById(10L)).thenReturn(set);
        when(subscribeMapper.selectCount(any())).thenReturn(0L);
        when(benefitSetMapper.deleteById(10L)).thenReturn(1);
        when(benefitRefMapper.delete(any())).thenReturn(0);

        ApiResponse<?> resp = (ApiResponse<?>) service.deleteBenefitSetsSetId(1L, "10");
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitSetMapper).deleteById(10L);
        verify(benefitRefMapper).delete(any());
    }

    @Test
    void testDeleteBenefitSetsSetId_NotFound() {
        when(benefitSetMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.deleteBenefitSetsSetId(1L, "999");
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testDeleteBenefitSetsSetId_ActiveSubscriptionsRejects() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setTenantId(1L);
        when(benefitSetMapper.selectById(10L)).thenReturn(set);
        when(subscribeMapper.selectCount(any())).thenReturn(1L);

        ApiResponse<?> resp = (ApiResponse<?>) service.deleteBenefitSetsSetId(1L, "10");
        assertThat(resp.isFail()).isTrue();
        verify(benefitSetMapper, never()).deleteById(anyLong());
    }

    // === Benefit Templates ===

    @Test
    void testGetBenefitTemplates_Success() {
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setId(100L);
        tmpl.setName("vip-monthly");
        when(benefitTmplSetMapper.selectList(any())).thenReturn(java.util.List.of(tmpl));
        UbmpBenefitTmplRef ref = new UbmpBenefitTmplRef();
        ref.setId(1L);
        ref.setSetId(100L);
        ref.setItemId(200L);
        ref.setQuota(10);
        ref.setRefreshCycle(1);
        ref.setRefreshCycleUnit("month");
        when(benefitTmplRefMapper.selectList(any())).thenReturn(java.util.List.of(ref));

        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitTemplates(1L);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows =
                (java.util.List<java.util.Map<String, Object>>) resp.getData();
        assertThat(rows).hasSize(1);
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> refs =
                (java.util.List<java.util.Map<String, Object>>) rows.get(0).get("refs");
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).get("item_id")).isNotNull();
        assertThat(refs.get(0).get("quota")).isEqualTo(10);
    }

    @Test
    void testGetBenefitTemplates_NoRefs_EmptyList() {
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setId(200L);
        when(benefitTmplSetMapper.selectList(any())).thenReturn(java.util.List.of(tmpl));
        when(benefitTmplRefMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        ApiResponse<?> resp = (ApiResponse<?>) service.getBenefitTemplates(1L);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> rows =
                (java.util.List<java.util.Map<String, Object>>) resp.getData();
        @SuppressWarnings("unchecked")
        java.util.List<java.util.Map<String, Object>> refs =
                (java.util.List<java.util.Map<String, Object>>) rows.get(0).get("refs");
        assertThat(refs).isEmpty();
    }

    // === Tenant proxy methods ===

    @Test
    void testGetUsersUseridAssets_Success() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setUserid("user-001");
        when(subscribeMapper.selectList(any())).thenReturn(java.util.List.of(sub));
        when(subscribeItemMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        ApiResponse<?> resp = (ApiResponse<?>) service.getUsersUseridAssets(1L, "user-001");
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetUsersUseridConsumes_Success() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        when(subscribeMapper.selectList(any())).thenReturn(java.util.List.of(sub));
        when(subscribeItemMapper.selectList(any())).thenReturn(java.util.Collections.emptyList());

        ApiResponse<?> resp = (ApiResponse<?>) service.getUsersUseridConsumes(1L, "user-001");
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testTenantPostSubscriptions_SetNotFound() {
        when(benefitSetMapper.selectById(anyLong())).thenReturn(null);

        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("user-001");
        req.setSetId("999");
        req.setExternalOrderId("ext-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptions(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testTenantPostSubscriptions_Success() {
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
        when(benefitRefMapper.selectList(any())).thenReturn(java.util.List.of(ref));

        when(subscribeMapper.selectOne(any())).thenReturn(null);
        doReturn(1).when(subscribeMapper).insert(any(UbmaSubscribe.class));
        doReturn(1).when(subscribeItemMapper).insert(any(UbmaSubscribeItem.class));

        PostSubscriptionsRequest req = new PostSubscriptionsRequest();
        req.setUserid("user-001");
        req.setSetId("10");
        req.setExternalOrderId("ext-sub-001");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptions(1L, req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testDisableSubscription_NotFound() {
        when(subscribeMapper.selectById(anyLong())).thenReturn(null);

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("违规封禁");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptionsSubscribeIdDisable(1L, "999", req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testDisableSubscription_Success() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setVersion(0);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("违规封禁");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptionsSubscribeIdDisable(1L, "1", req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper).update(any(), any());
    }

    @Test
    void testDisable_AlreadyCanceledRejects() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("CANCELED");
        when(subscribeMapper.selectById(1L)).thenReturn(sub);

        PostSubscriptionsSubscribeIdDisableRequest req = new PostSubscriptionsSubscribeIdDisableRequest();
        req.setReason("尝试禁用已取消订阅");

        ApiResponse<?> resp = (ApiResponse<?>) service.postSubscriptionsSubscribeIdDisable(1L, "1", req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCompensation_InactiveSubscriptionRejects() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("CANCELED");
        when(subscribeMapper.selectById(1L)).thenReturn(sub);

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("1");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(5);
        req.setAdjustType("ADD");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testCompensation_ReduceBelowConsumedRejects() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setQuotaLimit(30);
        sub.setPeriodConsumed(25);
        sub.setFrozenConsumed(0);
        sub.setVersion(0);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setSubscribeId(1L);
        item.setQuotaLimit(20);
        item.setPeriodConsumed(18);
        item.setFrozenConsumed(0);
        item.setVersion(0);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("1");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(5);
        req.setAdjustType("REDUCE");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isFail()).isTrue(); // 20-5=15 < 18 consumed
    }

    @Test
    void testCompensation_ReduceSetLevelBelowConsumedRejects() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setTenantId(1L);
        sub.setStatus("ACTIVE");
        sub.setQuotaLimit(30);
        sub.setPeriodConsumed(25);
        sub.setFrozenConsumed(2);
        sub.setVersion(0);
        when(subscribeMapper.selectById(1L)).thenReturn(sub);

        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setId(200L);
        item.setSubscribeId(1L);
        item.setQuotaLimit(40);
        item.setPeriodConsumed(5);
        item.setFrozenConsumed(0);
        item.setVersion(0);
        when(subscribeItemMapper.selectById(200L)).thenReturn(item);
        when(subscribeItemMapper.update(any(), any())).thenReturn(1);

        PostCompensationsRequest req = new PostCompensationsRequest();
        req.setSubscribeId("1");
        req.setSubsItemId("200");
        req.setItemId("100");
        req.setAdjustNum(5);
        req.setAdjustType("REDUCE");

        ApiResponse<?> resp = (ApiResponse<?>) service.postCompensations(1L, req);
        assertThat(resp.isFail()).isTrue(); // 30-5=25 < 25+2=27 consumed
    }

    @Test
    void testDeleteBenefitItemsItemId_HasSubscribeItemRefRejects() {
        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setId(100L);
        item.setTenantId(1L);
        when(benefitItemMapper.selectById(100L)).thenReturn(item);
        when(subscribeItemMapper.selectCount(any())).thenReturn(1L);

        ApiResponse<?> resp = (ApiResponse<?>) service.deleteBenefitItemsItemId(1L, "100");
        assertThat(resp.isFail()).isTrue();
        verify(benefitItemMapper, never()).deleteById(anyLong());
    }

    @Test
    void testPutBenefitItemsItemId_NotFound() {
        when(benefitItemMapper.selectById(anyLong())).thenReturn(null);
        PutBenefitItemsItemIdRequest req = new PutBenefitItemsItemIdRequest();
        req.setName("新名称");
        ApiResponse<?> resp = (ApiResponse<?>) service.putBenefitItemsItemId(1L, "999", req);
        assertThat(resp.isFail()).isTrue();
    }
}
