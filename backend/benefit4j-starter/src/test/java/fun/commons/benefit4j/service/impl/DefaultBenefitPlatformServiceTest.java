package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.web.ApiResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DefaultBenefitPlatformServiceTest {

    @InjectMocks
    private DefaultBenefitPlatformService service;

    @Mock private UbmaTenantMapper applicationMapper;
    @Mock private UbmaBenefitItemMapper benefitItemMapper;
    @Mock private UbmaBenefitSetMapper benefitSetMapper;
    @Mock private UbmpBenefitTmplSetMapper benefitTmplSetMapper;
    @Mock private UbmpBenefitTmplRefMapper benefitTmplRefMapper;
    @Mock private UbmpBenefitTmplItemMapper benefitTmplItemMapper;
    @Mock private UbmaSubscribeMapper subscribeMapper;
    @Mock private UbmaSubscribeItemMapper subscribeItemMapper;

    @BeforeAll
    static void initMpCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, UbmaTenant.class);
        TableInfoHelper.initTableInfo(assistant, UbmpBenefitTmplSet.class);
        TableInfoHelper.initTableInfo(assistant, UbmpBenefitTmplRef.class);
        TableInfoHelper.initTableInfo(assistant, UbmpBenefitTmplItem.class);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // === Tenants ===

    @Test
    void testPostTenants_Success() {
        doReturn(1).when(applicationMapper).insert(any(UbmaTenant.class));

        PostTenantsRequest req = new PostTenantsRequest();
        req.setName("测试应用");

        ApiResponse<?> resp = (ApiResponse<?>) service.postTenants(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(applicationMapper).insert(any(UbmaTenant.class));
    }

    @Test
    void testGetTenants_Success() {
        when(applicationMapper.selectList(any())).thenReturn(List.of(new UbmaTenant()));
        ApiResponse<?> resp = (ApiResponse<?>) service.getTenants();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testPutTenantsTenantId_Success() {
        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        when(applicationMapper.selectById(1L)).thenReturn(app);
        when(applicationMapper.update(any(), any())).thenReturn(1);

        PutTenantsTenantIdRequest req = new PutTenantsTenantIdRequest();
        req.setName("新名称");
        ApiResponse<?> resp = (ApiResponse<?>) service.putTenantsTenantId(1L, req);
        assertThat(resp.isSuccess()).isTrue();
    }

    // === Global Templates ===

    @Test
    void testPostGlobalTemplates_Success() {
        doReturn(1).when(benefitTmplSetMapper).insert(any(UbmpBenefitTmplSet.class));
        doReturn(1).when(benefitTmplRefMapper).insert(any(UbmpBenefitTmplRef.class));

        PostGlobalTemplatesRequest req = new PostGlobalTemplatesRequest();
        req.setName("标准VIP模板");
        req.setDuration(1);
        req.setDurationUnit("month");
        req.setQuota(30);
        PostGlobalTemplatesRequest.TemplateItemRef ref = new PostGlobalTemplatesRequest.TemplateItemRef();
        ref.setItemId("100");
        ref.setQuota(10);
        req.setRefs(List.of(ref));

        ApiResponse<?> resp = (ApiResponse<?>) service.postGlobalTemplates(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitTmplSetMapper).insert(any(UbmpBenefitTmplSet.class));
        verify(benefitTmplRefMapper).insert(any(UbmpBenefitTmplRef.class));
    }

    @Test
    void testGetGlobalTemplates_Success() {
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setId(3001L);
        tmpl.setName("[电商] 标准 VIP 月卡");
        tmpl.setDuration(1);
        tmpl.setDurationUnit("month");
        tmpl.setQuota(100);
        tmpl.setPriority(0);
        tmpl.setRefreshCycle(1);
        tmpl.setRefreshCycleUnit("month");
        tmpl.setStatus("ACTIVE");
        when(benefitTmplSetMapper.selectList(any())).thenReturn(List.of(tmpl));

        UbmpBenefitTmplRef ref1 = new UbmpBenefitTmplRef();
        ref1.setId(4001L);
        ref1.setSetId(3001L);
        ref1.setItemId(2001L);
        ref1.setQuota(4);
        ref1.setRefreshCycle(1);
        ref1.setRefreshCycleUnit("month");
        when(benefitTmplRefMapper.selectList(any())).thenReturn(List.of(ref1));

        ApiResponse<?> resp = (ApiResponse<?>) service.getGlobalTemplates();
        assertThat(resp.isSuccess()).isTrue();
        assertThat(resp.getData()).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows = (List<java.util.Map<String, Object>>) resp.getData();
        assertThat(rows).hasSize(1);
        java.util.Map<String, Object> row = rows.get(0);
        assertThat(row.get("name")).isEqualTo("[电商] 标准 VIP 月卡");
        assertThat(row.get("id")).isEqualTo(IdObfuscator.toOpenId(3001L));
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> refs = (List<java.util.Map<String, Object>>) row.get("refs");
        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).get("item_id")).isEqualTo(IdObfuscator.toOpenId(2001L));
        assertThat(refs.get(0).get("quota")).isEqualTo(4);
        assertThat(refs.get(0).get("refresh_cycle_unit")).isEqualTo("month");
    }

    @Test
    void testGetGlobalTemplates_Empty() {
        when(benefitTmplSetMapper.selectList(any())).thenReturn(List.of());
        ApiResponse<?> resp = (ApiResponse<?>) service.getGlobalTemplates();
        assertThat(resp.isSuccess()).isTrue();
        assertThat((List<?>) resp.getData()).isEmpty();
    }

    @Test
    void testPutGlobalTemplatesTmplId_Success() {
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setId(1L);
        when(benefitTmplSetMapper.selectById(1L)).thenReturn(tmpl);
        when(benefitTmplSetMapper.update(any(), any())).thenReturn(1);

        PutGlobalTemplatesTmplIdRequest req = new PutGlobalTemplatesTmplIdRequest();
        req.setName("更新模板名");
        req.setQuota(50);
        ApiResponse<?> resp = (ApiResponse<?>) service.putGlobalTemplatesTmplId(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitTmplSetMapper).update(any(), any());
    }

    @Test
    void testPutGlobalTemplatesTmplId_NotFound() {
        when(benefitTmplSetMapper.selectById(anyLong())).thenReturn(null);
        PutGlobalTemplatesTmplIdRequest req = new PutGlobalTemplatesTmplIdRequest();
        req.setName("更新模板名");
        ApiResponse<?> resp = (ApiResponse<?>) service.putGlobalTemplatesTmplId(999L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPutGlobalTemplatesTmplId_WithRefs() {
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setId(1L);
        when(benefitTmplSetMapper.selectById(1L)).thenReturn(tmpl);
        when(benefitTmplSetMapper.update(any(), any())).thenReturn(1);
        when(benefitTmplRefMapper.delete(any())).thenReturn(0);
        doReturn(1).when(benefitTmplRefMapper).insert(any(UbmpBenefitTmplRef.class));

        PutGlobalTemplatesTmplIdRequest req = new PutGlobalTemplatesTmplIdRequest();
        req.setName("更新模板名");
        PostGlobalTemplatesRequest.TemplateItemRef ref = new PostGlobalTemplatesRequest.TemplateItemRef();
        ref.setItemId("100");
        ref.setQuota(10);
        req.setRefs(List.of(ref));

        ApiResponse<?> resp = (ApiResponse<?>) service.putGlobalTemplatesTmplId(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitTmplRefMapper).delete(any());
        verify(benefitTmplRefMapper).insert(any(UbmpBenefitTmplRef.class));
    }

    @Test
    void testDeleteGlobalTemplatesTmplId_Success() {
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setId(1L);
        when(benefitTmplSetMapper.selectById(1L)).thenReturn(tmpl);
        when(benefitTmplSetMapper.deleteById(1L)).thenReturn(1);

        ApiResponse<?> resp = (ApiResponse<?>) service.deleteGlobalTemplatesTmplId(1L);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetStatisticsLiabilities() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setStatus("ACTIVE");
        sub.setQuotaLimit(30);
        sub.setTotalConsumed(10);
        sub.setFrozenConsumed(2);
        when(subscribeMapper.selectList(any())).thenReturn(List.of(sub));

        ApiResponse<?> resp = (ApiResponse<?>) service.getStatisticsLiabilities();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testDeleteGlobalTemplatesTmplId_NotFound() {
        when(benefitTmplSetMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.deleteGlobalTemplatesTmplId(999L);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testGetPlatformItems_WithFilters() {
        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setId(1001L);
        item.setTenantId(1L);
        item.setName("免邮特权");
        item.setIcon("ri-truck-line");
        item.setDescription("每月 4 次免邮, 单次订单不限金额");
        item.setDefaultDeduction(1);
        item.setStatus("ACTIVE");

        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        app.setName("官方租户");

        UbmaSubscribeItem si = new UbmaSubscribeItem();
        si.setItemId(1001L);
        si.setQuotaLimit(10000);
        si.setTotalConsumed(7320);

        // selectCount FIRST (no ORDER BY), then pagination list — verify call order with InOrder
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(benefitItemMapper);
        when(benefitItemMapper.selectCount(any())).thenReturn(1L);
        when(benefitItemMapper.selectList(any())).thenReturn(List.of(item));
        when(applicationMapper.selectBatchIds(any())).thenReturn(List.of(app));
        when(subscribeItemMapper.selectList(any())).thenReturn(List.of(si));

        ApiResponse<?> resp = (ApiResponse<?>) service.getPlatformItems(
            1L, "ACTIVE", "免邮", null, null, 1, 20);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> pageData =
            (java.util.Map<String, Object>) resp.getData();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows =
            (List<java.util.Map<String, Object>>) pageData.get("list");
        assertThat(rows).hasSize(1);
        java.util.Map<String, Object> row = rows.get(0);
        assertThat(row.get("id")).isEqualTo(IdObfuscator.toOpenId(1001L));
        assertThat(row.get("tenant_id")).isEqualTo(IdObfuscator.toOpenId(1L));
        assertThat(row.get("tenant_name")).isEqualTo("官方租户");
        assertThat(row.get("quota")).isEqualTo(10000);
        assertThat(row.get("used")).isEqualTo(7320);
        assertThat(row.get("usage_pct")).isEqualTo(73);
        assertThat(pageData.get("total")).isEqualTo(1L);
        assertThat(pageData.get("page")).isEqualTo(1);
        assertThat(pageData.get("size")).isEqualTo(20);

        // 关键: selectCount 必须在 selectList 之前调用 (PG 兼容 - 避开 ORDER BY 注入)
        inOrder.verify(benefitItemMapper).selectCount(any());
        inOrder.verify(benefitItemMapper).selectList(any());
    }

    @Test
    void testGetPlatformItems_EmptyFilters() {
        when(benefitItemMapper.selectCount(any())).thenReturn(0L);
        when(benefitItemMapper.selectList(any())).thenReturn(List.of());
        ApiResponse<?> resp = (ApiResponse<?>) service.getPlatformItems(null, null, null, null, null, 1, 20);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> pageData = (java.util.Map<String, Object>) resp.getData();
        assertThat(pageData.get("total")).isEqualTo(0L);
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> list = (List<java.util.Map<String, Object>>) pageData.get("list");
        assertThat(list).isEmpty();
    }

    @Test
    void testGetPlatformItems_BlankStringTreatedAsNull() {
        when(benefitItemMapper.selectCount(any())).thenReturn(0L);
        when(benefitItemMapper.selectList(any())).thenReturn(List.of());
        ApiResponse<?> resp = (ApiResponse<?>) service.getPlatformItems(null, "  ", "  免邮  ", null, null, 1, 20);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetPlatformBenefitSets_ReadsFromUbmaBenefitSet() {
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(2001L);
        set.setTenantId(1L);
        set.setName("标准VIP包");
        set.setDuration(1);
        set.setDurationUnit("month");
        set.setQuota(100);
        set.setPriority(10);
        set.setRefreshCycle(1);
        set.setRefreshCycleUnit("month");
        set.setStatus("ACTIVE");

        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        app.setName("官方租户");

        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setSetId(2001L);
        sub.setStatus("ACTIVE");

        when(benefitSetMapper.selectCount(any())).thenReturn(1L);
        when(benefitSetMapper.selectList(any())).thenReturn(List.of(set));
        when(applicationMapper.selectBatchIds(any())).thenReturn(List.of(app));
        when(subscribeMapper.selectList(any())).thenReturn(List.of(sub));

        ApiResponse<?> resp = (ApiResponse<?>) service.getPlatformBenefitSets(
            null, null, null, null, null, null, null, 1, 20);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> pageData =
            (java.util.Map<String, Object>) resp.getData();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> list =
            (List<java.util.Map<String, Object>>) pageData.get("list");
        assertThat(list).hasSize(1);
        java.util.Map<String, Object> row = list.get(0);
        assertThat(row.get("id")).isEqualTo(IdObfuscator.toOpenId(2001L));
        assertThat(row.get("tenant_id")).isEqualTo(IdObfuscator.toOpenId(1L));
        assertThat(row.get("tenant_name")).isEqualTo("官方租户");
        assertThat(row.get("name")).isEqualTo("标准VIP包");
        assertThat(row.get("subscribe_count")).isEqualTo(1);
        assertThat(pageData.get("total")).isEqualTo(1L);

        // 关键: 查询的是 ubma_benefit_set, 不是 ubmp_benefit_tmpl_set
        verify(benefitSetMapper).selectCount(any());
        verify(benefitSetMapper).selectList(any());
        verify(benefitTmplSetMapper, never()).selectCount(any());
        verify(benefitTmplSetMapper, never()).selectList(any());
    }

    @Test
    void testDeleteGlobalTemplatesTmplId_InvalidId() {
        ApiResponse<?> resp = (ApiResponse<?>) service.deleteGlobalTemplatesTmplId(999L);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPutGlobalTemplatesTmplId_InvalidId() {
        PutGlobalTemplatesTmplIdRequest req = new PutGlobalTemplatesTmplIdRequest();
        req.setName("更新模板名");
        ApiResponse<?> resp = (ApiResponse<?>) service.putGlobalTemplatesTmplId(999L, req);
        assertThat(resp.isFail()).isTrue();
    }

    // === Item Templates ===

    @Test
    void testPostItemTemplates_Success() {
        doReturn(1).when(benefitTmplItemMapper).insert(any(UbmpBenefitTmplItem.class));

        PostItemTemplatesRequest req = new PostItemTemplatesRequest();
        req.setName("免邮特权");
        req.setIcon("ri-truck-line");
        req.setDefaultDeduction(1);

        ApiResponse<?> resp = (ApiResponse<?>) service.postItemTemplates(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitTmplItemMapper).insert(any(UbmpBenefitTmplItem.class));
    }

    @Test
    void testPostItemTemplates_DefaultDeductionApplied() {
        doReturn(1).when(benefitTmplItemMapper).insert(any(UbmpBenefitTmplItem.class));

        PostItemTemplatesRequest req = new PostItemTemplatesRequest();
        req.setName("会员折扣");

        ApiResponse<?> resp = (ApiResponse<?>) service.postItemTemplates(req);
        assertThat(resp.isSuccess()).isTrue();

        ArgumentCaptor<UbmpBenefitTmplItem> captor = ArgumentCaptor.forClass(UbmpBenefitTmplItem.class);
        verify(benefitTmplItemMapper).insert(captor.capture());
        UbmpBenefitTmplItem inserted = captor.getValue();
        assertThat(inserted.getDefaultDeduction()).isEqualTo(1);
        assertThat(inserted.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void testGetItemTemplates_Success() {
        when(benefitTmplItemMapper.selectList(any())).thenReturn(List.of(new UbmpBenefitTmplItem()));
        ApiResponse<?> resp = (ApiResponse<?>) service.getItemTemplates();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetItemTemplates_Empty() {
        when(benefitTmplItemMapper.selectList(any())).thenReturn(List.of());
        ApiResponse<?> resp = (ApiResponse<?>) service.getItemTemplates();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testPutItemTemplatesItemId_Success() {
        UbmpBenefitTmplItem item = new UbmpBenefitTmplItem();
        item.setId(1L);
        when(benefitTmplItemMapper.selectById(1L)).thenReturn(item);
        when(benefitTmplItemMapper.update(any(), any())).thenReturn(1);

        PutItemTemplatesItemIdRequest req = new PutItemTemplatesItemIdRequest();
        req.setDefaultDeduction(2);
        ApiResponse<?> resp = (ApiResponse<?>) service.putItemTemplatesItemId(1L, req);
        assertThat(resp.isSuccess()).isTrue();
        verify(benefitTmplItemMapper).update(any(), any());
    }

    @Test
    void testPutItemTemplatesItemId_NotFound() {
        when(benefitTmplItemMapper.selectById(anyLong())).thenReturn(null);
        PutItemTemplatesItemIdRequest req = new PutItemTemplatesItemIdRequest();
        ApiResponse<?> resp = (ApiResponse<?>) service.putItemTemplatesItemId(999L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testPutItemTemplatesItemId_InvalidId() {
        PutItemTemplatesItemIdRequest req = new PutItemTemplatesItemIdRequest();
        ApiResponse<?> resp = (ApiResponse<?>) service.putItemTemplatesItemId(999L, req);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testDeleteItemTemplatesItemId_Success() {
        UbmpBenefitTmplItem item = new UbmpBenefitTmplItem();
        item.setId(1L);
        when(benefitTmplItemMapper.selectById(1L)).thenReturn(item);
        when(benefitTmplItemMapper.deleteById(1L)).thenReturn(1);

        ApiResponse<?> resp = (ApiResponse<?>) service.deleteItemTemplatesItemId(1L);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testDeleteItemTemplatesItemId_NotFound() {
        when(benefitTmplItemMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.deleteItemTemplatesItemId(999L);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testDeleteItemTemplatesItemId_InvalidId() {
        ApiResponse<?> resp = (ApiResponse<?>) service.deleteItemTemplatesItemId(999L);
        assertThat(resp.isFail()).isTrue();
    }

    // === Application Secret Reset ===

    @Test
    void testPostTenantsTenantIdSecret_Success() {
        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        app.setTenantSecret("old-secret");
        when(applicationMapper.selectById(1L)).thenReturn(app);
        when(applicationMapper.update(any(), any())).thenReturn(1);

        ApiResponse<?> resp = (ApiResponse<?>) service.postTenantsTenantIdSecret(1L);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) resp.getData();
        assertThat(data.get("id")).isEqualTo(IdObfuscator.toOpenId(1L));
        assertThat(data.get("tenant_secret").toString()).isNotEqualTo("old-secret");
    }

    @Test
    void testPostTenantsTenantIdSecret_NotFound() {
        when(applicationMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.postTenantsTenantIdSecret(999L);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testGetTenantsTenantIdSecret_Success() {
        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        app.setName("测试");
        app.setTenantSecret("real-plaintext-secret");
        when(applicationMapper.selectById(1L)).thenReturn(app);

        ApiResponse<?> resp = (ApiResponse<?>) service.getTenantsTenantIdSecret(1L);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) resp.getData();
        assertThat(data.get("tenant_secret")).isEqualTo("real-plaintext-secret");
    }

    @Test
    void testGetTenantsTenantIdSecret_NotFound() {
        when(applicationMapper.selectById(anyLong())).thenReturn(null);
        ApiResponse<?> resp = (ApiResponse<?>) service.getTenantsTenantIdSecret(999L);
        assertThat(resp.isFail()).isTrue();
    }

    @Test
    void testGetTenants_MasksSecret() {
        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        app.setName("测试");
        app.setTenantSecret("abcdef1234567890abcdef1234567890");
        when(applicationMapper.selectList(any())).thenReturn(List.of(app));
        when(subscribeMapper.selectList(any())).thenReturn(List.of());

        ApiResponse<?> resp = (ApiResponse<?>) service.getTenants();
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows = (List<java.util.Map<String, Object>>) resp.getData();
        assertThat(rows).hasSize(1);
        String masked = (String) rows.get(0).get("tenant_secret");
        assertThat(masked).contains("•");
        assertThat(masked).isNotEqualTo("abcdef1234567890abcdef1234567890");
    }

    // === Application Description ===

    @Test
    void testPostTenants_WithDescription() {
        doReturn(1).when(applicationMapper).insert(any(UbmaTenant.class));
        PostTenantsRequest req = new PostTenantsRequest();
        req.setName("测试应用");
        req.setDescription("示例描述");
        ApiResponse<?> resp = (ApiResponse<?>) service.postTenants(req);
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) resp.getData();
        assertThat(data.get("description")).isEqualTo("示例描述");
    }

    @Test
    void testGetTenants_WithSubscriptionCount() {
        UbmaTenant app = new UbmaTenant();
        app.setId(1L);
        app.setName("测试");
        app.setDescription("desc");
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setTenantId(1L);
        when(applicationMapper.selectList(any())).thenReturn(List.of(app));
        when(subscribeMapper.selectList(any())).thenReturn(List.of(sub, sub));
        ApiResponse<?> resp = (ApiResponse<?>) service.getTenants();
        assertThat(resp.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> rows = (List<java.util.Map<String, Object>>) resp.getData();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("subscription_count")).isEqualTo(2);
        assertThat(rows.get(0).get("description")).isEqualTo("desc");
    }
}