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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DefaultBenefitOpsServiceTest {

    @InjectMocks
    private DefaultBenefitOpsService service;

    @Mock private UbmaSubscribeMapper subscribeMapper;
    @Mock private UbmaSubscribeItemMapper subscribeItemMapper;
    @Mock private UbmaBenefitSetMapper benefitSetMapper;

    @BeforeAll
    static void initMpCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace("test");
        TableInfoHelper.initTableInfo(assistant, UbmaSubscribe.class);
        TableInfoHelper.initTableInfo(assistant, UbmaSubscribeItem.class);
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetHealth() {
        ApiResponse<?> resp = (ApiResponse<?>) service.getHealth();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testGetMetrics() {
        when(subscribeMapper.selectCount(any())).thenReturn(5L);
        ApiResponse<?> resp = (ApiResponse<?>) service.getMetrics();
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testPostCacheEvict() {
        PostCacheEvictRequest req = new PostCacheEvictRequest();
        req.setCacheType("SUBSCRIBE");
        ApiResponse<?> resp = (ApiResponse<?>) service.postCacheEvict(req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testRefreshCycles_NoSubscriptionsToRefresh() {
        when(subscribeMapper.selectList(any())).thenReturn(Collections.emptyList());

        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        ApiResponse<?> resp = (ApiResponse<?>) service.postJobsRefreshCycles(req);
        assertThat(resp.isSuccess()).isTrue();
    }

    @Test
    void testRefreshCycles_ExpiredSubscription() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setAppId(1L);
        sub.setSetId(10L);
        sub.setStatus("ACTIVE");
        sub.setDateEnd(OffsetDateTime.now().minusDays(1)); // expired
        sub.setVersion(0);

        when(subscribeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(sub)));
        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);

        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        ApiResponse<?> resp = (ApiResponse<?>) service.postJobsRefreshCycles(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper).update(any(), any()); // status → EXPIRED
    }

    @Test
    void testRefreshCycles_RefreshActiveSubscription() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setAppId(1L);
        sub.setSetId(10L);
        sub.setStatus("ACTIVE");
        sub.setDateEnd(OffsetDateTime.now().plusDays(30)); // not expired
        sub.setPeriodConsumed(5);
        sub.setFrozenConsumed(0);
        sub.setVersion(0);

        when(subscribeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(sub)));

        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setId(10L);
        set.setRefreshCycle(1);
        set.setRefreshCycleUnit("day");
        when(benefitSetMapper.selectById(10L)).thenReturn(set);

        when(subscribeMapper.selectById(1L)).thenReturn(sub);
        when(subscribeMapper.update(any(), any())).thenReturn(1);
        when(subscribeItemMapper.selectList(any())).thenReturn(Collections.emptyList());

        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        ApiResponse<?> resp = (ApiResponse<?>) service.postJobsRefreshCycles(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper).update(any(), any()); // periodConsumed → 0
    }

    @Test
    void testRefreshCycles_DryRun() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setAppId(1L);
        sub.setSetId(10L);
        sub.setStatus("ACTIVE");
        sub.setDateEnd(OffsetDateTime.now().plusDays(30));
        sub.setVersion(0);

        when(subscribeMapper.selectList(any())).thenReturn(new ArrayList<>(List.of(sub)));

        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        req.setDryRun(true);
        ApiResponse<?> resp = (ApiResponse<?>) service.postJobsRefreshCycles(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper, never()).update(any(), any()); // dry run, no updates
    }

    @Test
    void testRefreshCycles_FrozenSkips() {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setId(1L);
        sub.setAppId(1L);
        sub.setStatus("ACTIVE");
        sub.setFrozenConsumed(3);
        sub.setNextRefreshTime(OffsetDateTime.now().minusDays(1));
        sub.setDateEnd(OffsetDateTime.now().plusDays(30));
        sub.setVersion(0);
        when(subscribeMapper.selectList(any())).thenReturn(List.of(sub));
        when(subscribeMapper.selectById(1L)).thenReturn(sub);

        PostJobsRefreshCyclesRequest req = new PostJobsRefreshCyclesRequest();
        ApiResponse<?> resp = (ApiResponse<?>) service.postJobsRefreshCycles(req);
        assertThat(resp.isSuccess()).isTrue();
        verify(subscribeMapper, never()).update(any(), any());
    }
}
