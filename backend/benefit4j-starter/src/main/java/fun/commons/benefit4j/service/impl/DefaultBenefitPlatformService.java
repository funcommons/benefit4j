package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.benefit4j.service.BenefitPlatformService;
import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultBenefitPlatformService implements BenefitPlatformService {

    private final UbmaApplicationMapper applicationMapper;
    private final UbmaBenefitItemMapper benefitItemMapper;
    private final UbmaBenefitSetMapper benefitSetMapper;
    private final UbmpBenefitTmplSetMapper benefitTmplSetMapper;
    private final UbmpBenefitTmplRefMapper benefitTmplRefMapper;
    private final UbmpBenefitTmplItemMapper benefitTmplItemMapper;
    private final UbmaSubscribeMapper subscribeMapper;
    private final UbmaSubscribeItemMapper subscribeItemMapper;
    private final UbmaConsumeMapper consumeMapper;

    @Override
    @Transactional
    @Auditable(action = "APP_CREATE", targetType = "application", targetIdSpel = "#req.name")
    public Object postApplications(PostApplicationsRequest req) {
        OffsetDateTime now = OffsetDateTime.now();
        UbmaApplication app = new UbmaApplication();
        app.setAppSecret(UUID.randomUUID().toString().replace("-", ""));
        app.setName(req.getName());
        app.setDescription(req.getDescription() != null ? req.getDescription() : "");
        app.setStatus("ACTIVE");
        app.setExt(req.getExt());
        app.setCreatedAt(now);
        app.setUpdatedAt(now);
        applicationMapper.insert(app);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdObfuscator.toOpenId(app.getId()));
        result.put("name", app.getName());
        result.put("app_secret", app.getAppSecret());
        result.put("name", app.getName());
        result.put("description", app.getDescription());
        result.put("status", app.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object getApplications() {
        List<UbmaApplication> apps = applicationMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, Integer> subCountByAppid = countSubscriptionsByAppid();
        for (UbmaApplication app : apps) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(app.getId()));
            row.put("app_secret", maskSecret(app.getAppSecret()));
            row.put("name", app.getName());
            row.put("description", app.getDescription());
            row.put("status", app.getStatus());
            row.put("created_at", app.getCreatedAt());
            row.put("updated_at", app.getUpdatedAt());
            row.put("subscription_count", app.getId() != null
                    ? subCountByAppid.getOrDefault(app.getId(), 0) : 0);
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    /**
     * 列表查询返回的 app_secret 已脱敏处理,只有创建 / 重置接口返回完整密钥
     */
    private static String maskSecret(String raw) {
        if (raw == null || raw.length() <= 8) return "••••••";
        return raw.substring(0, 4) + "•".repeat(Math.min(raw.length() - 8, 24)) + raw.substring(raw.length() - 4);
    }

    private Map<Long, Integer> countSubscriptionsByAppid() {
        List<UbmaSubscribe> subs = subscribeMapper.selectList(null);
        Map<Long, Integer> map = new java.util.HashMap<>();
        for (UbmaSubscribe s : subs) {
            if (s.getAppId() == null) continue;
            map.merge(s.getAppId(), 1, Integer::sum);
        }
        return map;
    }

    @Override
    @Transactional
    @Auditable(action = "APP_UPDATE", targetType = "application", targetIdSpel = "#appId")
    public Object putApplicationsAppId(Long appId, PutApplicationsAppIdRequest req) {
        UbmaApplication app = applicationMapper.selectById(appId);
        if (app == null) {
            return ApiResponse.fail(404, "应用不存在");
        }

        LambdaUpdateWrapper<UbmaApplication> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaApplication::getId, app.getId());
        if (req.getName() != null) wrapper.set(UbmaApplication::getName, req.getName());
        if (req.getDescription() != null) wrapper.set(UbmaApplication::getDescription, req.getDescription());
        if (req.getStatus() != null) wrapper.set(UbmaApplication::getStatus, req.getStatus());
        if (req.getExt() != null) wrapper.set(UbmaApplication::getExt, req.getExt());
        wrapper.set(UbmaApplication::getUpdatedAt, OffsetDateTime.now());
        applicationMapper.update(null, wrapper);

        return ApiResponse.success("ok");
    }

    @Override
    @Transactional
    @Auditable(action = "APP_RESET_SECRET", targetType = "application", targetIdSpel = "#appId")
    public Object postApplicationsAppIdSecret(Long appId) {
        UbmaApplication app = applicationMapper.selectById(appId);
        if (app == null) {
            return ApiResponse.fail(404, "应用不存在");
        }

        String newSecret = UUID.randomUUID().toString().replace("-", "");
        // 走 updateById 让 LazyEncryptedFieldTypeHandler (framework4j) 自动加密 app_secret;
        // ext 字段标了 jdbcType=JdbcType.OTHER 修复 JSONB update cast, 全量 updateById 不再报
        // "column ext is of type jsonb but expression is of type varchar"
        app.setAppSecret(newSecret);
        app.setUpdatedAt(OffsetDateTime.now());
        applicationMapper.updateById(app);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdObfuscator.toOpenId(app.getId()));
        result.put("name", app.getName());
        result.put("app_secret", newSecret);
        return ApiResponse.success(result);
    }

    @Override
    public Object getApplicationsAppIdSecret(Long appId) {
        UbmaApplication app = applicationMapper.selectById(appId);
        if (app == null) {
            return ApiResponse.fail(404, "应用不存在");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdObfuscator.toOpenId(app.getId()));
        result.put("name", app.getName());
        result.put("app_secret", app.getAppSecret());
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object postGlobalTemplates(PostGlobalTemplatesRequest req) {
        OffsetDateTime now = OffsetDateTime.now();
        UbmpBenefitTmplSet tmpl = new UbmpBenefitTmplSet();
        tmpl.setName(req.getName());
        tmpl.setDuration(req.getDuration());
        tmpl.setDurationUnit(req.getDurationUnit());
        tmpl.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        tmpl.setQuota(req.getQuota() != null ? req.getQuota() : 0);
        tmpl.setRefreshCycle(req.getRefreshCycle());
        tmpl.setRefreshCycleUnit(req.getRefreshCycleUnit());
        tmpl.setStatus("ACTIVE");
        tmpl.setCreatedAt(now);
        tmpl.setUpdatedAt(now);
        benefitTmplSetMapper.insert(tmpl);

        if (req.getRefs() != null) {
            for (PostGlobalTemplatesRequest.TemplateItemRef ref : req.getRefs()) {
                Long refItemId = safeParseId(ref.getItemId());
                if (refItemId == null) return ApiResponse.fail(400, "无效的权益项ID");
                UbmpBenefitTmplRef tmplRef = new UbmpBenefitTmplRef();
                tmplRef.setSetId(tmpl.getId());
                tmplRef.setItemId(refItemId);
                tmplRef.setQuota(ref.getQuota() != null ? ref.getQuota() : 0);
                tmplRef.setRefreshCycle(ref.getRefreshCycle());
                tmplRef.setRefreshCycleUnit(ref.getRefreshCycleUnit());
                tmplRef.setCreatedAt(now);
                tmplRef.setUpdatedAt(now);
                benefitTmplRefMapper.insert(tmplRef);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tmpl_id", IdObfuscator.toOpenId(tmpl.getId()));
        result.put("name", tmpl.getName());
        result.put("status", tmpl.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    public Object getGlobalTemplates() {
        List<UbmpBenefitTmplSet> templates = benefitTmplSetMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        Map<Long, List<Map<String, Object>>> refsByTmpl = new java.util.HashMap<>();
        if (!templates.isEmpty()) {
            List<Long> tmplIds = templates.stream().map(UbmpBenefitTmplSet::getId).toList();
            LambdaQueryWrapper<UbmpBenefitTmplRef> refQuery = new LambdaQueryWrapper<>();
            refQuery.in(UbmpBenefitTmplRef::getSetId, tmplIds);
            List<UbmpBenefitTmplRef> allRefs = benefitTmplRefMapper.selectList(refQuery);
            for (UbmpBenefitTmplRef r : allRefs) {
                Map<String, Object> ref = new LinkedHashMap<>();
                ref.put("id", IdObfuscator.toOpenId(r.getId()));
                ref.put("item_id", IdObfuscator.toOpenId(r.getItemId()));
                ref.put("quota", r.getQuota());
                ref.put("refresh_cycle", r.getRefreshCycle());
                ref.put("refresh_cycle_unit", r.getRefreshCycleUnit());
                refsByTmpl.computeIfAbsent(r.getSetId(), k -> new ArrayList<>()).add(ref);
            }
        }
        for (UbmpBenefitTmplSet tmpl : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(tmpl.getId()));
            row.put("name", tmpl.getName());
            row.put("duration", tmpl.getDuration());
            row.put("duration_unit", tmpl.getDurationUnit());
            row.put("priority", tmpl.getPriority());
            row.put("quota", tmpl.getQuota());
            row.put("refresh_cycle", tmpl.getRefreshCycle());
            row.put("refresh_cycle_unit", tmpl.getRefreshCycleUnit());
            row.put("status", tmpl.getStatus());
            row.put("refs", refsByTmpl.getOrDefault(tmpl.getId(), java.util.Collections.emptyList()));
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object putGlobalTemplatesTmplId(Long tmplId, PutGlobalTemplatesTmplIdRequest req) {
        UbmpBenefitTmplSet tmpl = benefitTmplSetMapper.selectById(tmplId);
        if (tmpl == null) {
            return ApiResponse.fail(404, "模板不存在");
        }

        LambdaUpdateWrapper<UbmpBenefitTmplSet> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmpBenefitTmplSet::getId, tmpl.getId());
        if (req.getName() != null) wrapper.set(UbmpBenefitTmplSet::getName, req.getName());
        if (req.getDuration() != null) wrapper.set(UbmpBenefitTmplSet::getDuration, req.getDuration());
        if (req.getDurationUnit() != null) wrapper.set(UbmpBenefitTmplSet::getDurationUnit, req.getDurationUnit());
        if (req.getPriority() != null) wrapper.set(UbmpBenefitTmplSet::getPriority, req.getPriority());
        if (req.getQuota() != null) wrapper.set(UbmpBenefitTmplSet::getQuota, req.getQuota());
        if (req.getRefreshCycle() != null) wrapper.set(UbmpBenefitTmplSet::getRefreshCycle, req.getRefreshCycle());
        if (req.getRefreshCycleUnit() != null) wrapper.set(UbmpBenefitTmplSet::getRefreshCycleUnit, req.getRefreshCycleUnit());
        wrapper.set(UbmpBenefitTmplSet::getUpdatedAt, OffsetDateTime.now());
        benefitTmplSetMapper.update(null, wrapper);

        if (req.getRefs() != null) {
            LambdaQueryWrapper<UbmpBenefitTmplRef> deleteQuery = new LambdaQueryWrapper<>();
            deleteQuery.eq(UbmpBenefitTmplRef::getSetId, tmpl.getId());
            benefitTmplRefMapper.delete(deleteQuery);

            for (PostGlobalTemplatesRequest.TemplateItemRef ref : req.getRefs()) {
                Long refItemId = safeParseId(ref.getItemId());
                if (refItemId == null) return ApiResponse.fail(400, "无效的权益项ID");
                UbmpBenefitTmplRef tmplRef = new UbmpBenefitTmplRef();
                tmplRef.setSetId(tmpl.getId());
                tmplRef.setItemId(refItemId);
                tmplRef.setQuota(ref.getQuota() != null ? ref.getQuota() : 0);
                tmplRef.setRefreshCycle(ref.getRefreshCycle());
                tmplRef.setRefreshCycleUnit(ref.getRefreshCycleUnit());
                tmplRef.setCreatedAt(OffsetDateTime.now());
                tmplRef.setUpdatedAt(OffsetDateTime.now());
                benefitTmplRefMapper.insert(tmplRef);
            }
        }

        return ApiResponse.success("ok");
    }

    @Override
    @Transactional
    public Object deleteGlobalTemplatesTmplId(Long tmplId) {
        UbmpBenefitTmplSet tmpl = benefitTmplSetMapper.selectById(tmplId);
        if (tmpl == null) {
            return ApiResponse.fail(404, "模板不存在");
        }
        benefitTmplSetMapper.deleteById(tmpl.getId());

        LambdaQueryWrapper<UbmpBenefitTmplRef> refDeleteQuery = new LambdaQueryWrapper<>();
        refDeleteQuery.eq(UbmpBenefitTmplRef::getSetId, tmpl.getId());
        benefitTmplRefMapper.delete(refDeleteQuery);

        return ApiResponse.success("ok");
    }

    @Override
    public Object getPlatformItems(Long appId,
                                    String status,
                                    String keyword,
                                    OffsetDateTime createdAtStart,
                                    OffsetDateTime createdAtEnd,
                                    Integer page,
                                    Integer size) {
        LambdaQueryWrapper<UbmaBenefitItem> query = new LambdaQueryWrapper<>();
        if (appId != null) query.eq(UbmaBenefitItem::getAppId, appId);
        if (status != null && !status.isBlank()) query.eq(UbmaBenefitItem::getStatus, status.trim());
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            query.and(w -> w.like(UbmaBenefitItem::getName, kw)
                    .or().like(UbmaBenefitItem::getDescription, kw));
        }
        if (createdAtStart != null) query.ge(UbmaBenefitItem::getCreatedAt, createdAtStart);
        if (createdAtEnd != null) query.le(UbmaBenefitItem::getCreatedAt, createdAtEnd);

        // selectCount 必须在 orderByDesc + last("LIMIT..") 之前调用 (PostgreSQL 兼容).
        long total = benefitItemMapper.selectCount(query);

        query.orderByDesc(UbmaBenefitItem::getCreatedAt);
        int p = pageOr1(page);
        int s = sizeOr20(size);
        query.last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<UbmaBenefitItem> items = benefitItemMapper.selectList(query);

        // 批量拉租户名称
        Set<Long> appIds = items.stream().map(UbmaBenefitItem::getAppId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> appNameById = new HashMap<>();
        if (!appIds.isEmpty()) {
            for (UbmaApplication a : applicationMapper.selectBatchIds(appIds)) {
                appNameById.put(a.getId(), a.getName());
            }
        }

        // 应用层聚合: 当前页每个 item 的 quota/used 来自 subscribe_item 汇总
        Map<Long, long[]> stats = new HashMap<>();
        if (!items.isEmpty()) {
            List<Long> itemIds = items.stream().map(UbmaBenefitItem::getId).toList();
            LambdaQueryWrapper<UbmaSubscribeItem> sQ = new LambdaQueryWrapper<>();
            sQ.in(UbmaSubscribeItem::getItemId, itemIds);
            for (UbmaSubscribeItem si : subscribeItemMapper.selectList(sQ)) {
                long[] arr = stats.computeIfAbsent(si.getItemId(), k -> new long[2]);
                arr[0] += safeInt(si.getQuotaLimit());
                arr[1] += safeInt(si.getTotalConsumed());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (UbmaBenefitItem bi : items) {
            long[] arr = stats.getOrDefault(bi.getId(), new long[]{0, 0});
            int quota = (int) arr[0];
            int used = (int) arr[1];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(bi.getId()));
            row.put("app_id", IdObfuscator.toOpenId(bi.getAppId()));
            row.put("tenant_name", appNameById.getOrDefault(bi.getAppId(), ""));
            row.put("name", bi.getName());
            row.put("icon", bi.getIcon());
            row.put("description", bi.getDescription());
            row.put("default_deduction", bi.getDefaultDeduction());
            row.put("status", bi.getStatus());
            row.put("quota", quota);
            row.put("used", used);
            row.put("usage_pct", quota > 0 ? (int) Math.round(used * 100.0 / quota) : 0);
            row.put("created_at", bi.getCreatedAt());
            row.put("updated_at", bi.getUpdatedAt());
            list.add(row);
        }

        return ApiResponse.success(buildPage(list, total, p, s));
    }

    @Override
    public Object getPlatformBenefitSets(Long appId,
                                          String status,
                                          String keyword,
                                          Integer priorityMin,
                                          Integer priorityMax,
                                          OffsetDateTime createdAtStart,
                                          OffsetDateTime createdAtEnd,
                                          Integer page,
                                          Integer size) {
        LambdaQueryWrapper<UbmaBenefitSet> query = new LambdaQueryWrapper<>();
        if (appId != null) query.eq(UbmaBenefitSet::getAppId, appId);
        if (status != null && !status.isBlank()) query.eq(UbmaBenefitSet::getStatus, status.trim());
        if (keyword != null && !keyword.isBlank()) query.like(UbmaBenefitSet::getName, keyword.trim());
        if (priorityMin != null) query.ge(UbmaBenefitSet::getPriority, priorityMin);
        if (priorityMax != null) query.le(UbmaBenefitSet::getPriority, priorityMax);
        if (createdAtStart != null) query.ge(UbmaBenefitSet::getCreatedAt, createdAtStart);
        if (createdAtEnd != null) query.le(UbmaBenefitSet::getCreatedAt, createdAtEnd);

        // selectCount 必须在 orderByDesc + last("LIMIT..") 之前调用 (PostgreSQL 兼容).
        long total = benefitSetMapper.selectCount(query);

        query.orderByDesc(UbmaBenefitSet::getCreatedAt);
        int p = pageOr1(page);
        int s = sizeOr20(size);
        query.last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<UbmaBenefitSet> sets = benefitSetMapper.selectList(query);

        // 批量拉租户名称
        Set<Long> appIds = sets.stream().map(UbmaBenefitSet::getAppId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> appNameById = new HashMap<>();
        if (!appIds.isEmpty()) {
            for (UbmaApplication a : applicationMapper.selectBatchIds(appIds)) {
                appNameById.put(a.getId(), a.getName());
            }
        }

        // 汇总活跃订阅数 (ACTIVE + EXHAUSTED)
        Map<Long, Integer> subCountBySet = new HashMap<>();
        if (!sets.isEmpty()) {
            List<Long> setIds = sets.stream().map(UbmaBenefitSet::getId).toList();
            LambdaQueryWrapper<UbmaSubscribe> sQ = new LambdaQueryWrapper<>();
            sQ.in(UbmaSubscribe::getSetId, setIds)
                    .in(UbmaSubscribe::getStatus, java.util.List.of("ACTIVE", "EXHAUSTED"));
            for (UbmaSubscribe sub : subscribeMapper.selectList(sQ)) {
                subCountBySet.merge(sub.getSetId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (UbmaBenefitSet bs : sets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(bs.getId()));
            row.put("app_id", IdObfuscator.toOpenId(bs.getAppId()));
            row.put("tenant_name", appNameById.getOrDefault(bs.getAppId(), ""));
            row.put("name", bs.getName());
            row.put("duration", bs.getDuration());
            row.put("duration_unit", bs.getDurationUnit());
            row.put("priority", bs.getPriority());
            row.put("quota", bs.getQuota());
            row.put("refresh_cycle", bs.getRefreshCycle());
            row.put("refresh_cycle_unit", bs.getRefreshCycleUnit());
            row.put("timing_mode", bs.getTimingMode());
            row.put("quota_unit", bs.getQuotaUnit());
            row.put("status", bs.getStatus());
            row.put("subscribe_count", subCountBySet.getOrDefault(bs.getId(), 0));
            row.put("created_at", bs.getCreatedAt());
            row.put("updated_at", bs.getUpdatedAt());
            list.add(row);
        }

        return ApiResponse.success(buildPage(list, total, p, s));
    }

    @Override
    public Object getStatisticsLiabilities() {
        LambdaQueryWrapper<UbmaSubscribe> query = new LambdaQueryWrapper<>();
        query.eq(UbmaSubscribe::getStatus, "ACTIVE");
        List<UbmaSubscribe> activeSubs = subscribeMapper.selectList(query);

        long totalSubscriptions = activeSubs.size();
        long totalQuota = 0;
        long totalConsumed = 0;
        long totalFrozen = 0;
        for (UbmaSubscribe sub : activeSubs) {
            if (sub.getQuotaLimit() != null) totalQuota += sub.getQuotaLimit();
            if (sub.getTotalConsumed() != null) totalConsumed += sub.getTotalConsumed();
            if (sub.getFrozenConsumed() != null) totalFrozen += sub.getFrozenConsumed();
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_active_subscriptions", totalSubscriptions);
        stats.put("total_quota_committed", totalQuota);
        stats.put("total_consumed", totalConsumed);
        stats.put("total_frozen", totalFrozen);
        stats.put("total_liability", totalQuota - totalConsumed - totalFrozen);
        return ApiResponse.success(stats);
    }

    private Long safeParseId(String id) {
        if (id == null || id.isBlank()) return null;
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            try {
                return IdObfuscator.fromOpenId(id);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    @Override
    @Transactional
    public Object postItemTemplates(PostItemTemplatesRequest req) {
        OffsetDateTime now = OffsetDateTime.now();
        UbmpBenefitTmplItem item = new UbmpBenefitTmplItem();
        item.setName(req.getName());
        item.setIcon(req.getIcon() != null ? req.getIcon() : "");
        item.setDescription(req.getDescription() != null ? req.getDescription() : "");
        item.setDefaultDeduction(req.getDefaultDeduction() != null ? req.getDefaultDeduction() : 1);
        item.setStatus(req.getStatus() != null ? req.getStatus() : "ACTIVE");
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        benefitTmplItemMapper.insert(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", IdObfuscator.toOpenId(item.getId()));
        result.put("name", item.getName());
        result.put("status", item.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    public Object getItemTemplates() {
        List<UbmpBenefitTmplItem> templates = benefitTmplItemMapper.selectList(null);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UbmpBenefitTmplItem t : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(t.getId()));
            row.put("name", t.getName());
            row.put("icon", t.getIcon());
            row.put("description", t.getDescription());
            row.put("default_deduction", t.getDefaultDeduction());
            row.put("status", t.getStatus());
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object putItemTemplatesItemId(Long itemId, PutItemTemplatesItemIdRequest req) {
        UbmpBenefitTmplItem item = benefitTmplItemMapper.selectById(itemId);
        if (item == null) {
            return ApiResponse.fail(404, "权益项模板不存在");
        }

        LambdaUpdateWrapper<UbmpBenefitTmplItem> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmpBenefitTmplItem::getId, item.getId());
        if (req.getName() != null) wrapper.set(UbmpBenefitTmplItem::getName, req.getName());
        if (req.getIcon() != null) wrapper.set(UbmpBenefitTmplItem::getIcon, req.getIcon());
        if (req.getDescription() != null) wrapper.set(UbmpBenefitTmplItem::getDescription, req.getDescription());
        if (req.getDefaultDeduction() != null) wrapper.set(UbmpBenefitTmplItem::getDefaultDeduction, req.getDefaultDeduction());
        if (req.getStatus() != null) wrapper.set(UbmpBenefitTmplItem::getStatus, req.getStatus());
        wrapper.set(UbmpBenefitTmplItem::getUpdatedAt, OffsetDateTime.now());
        benefitTmplItemMapper.update(null, wrapper);

        return ApiResponse.success("ok");
    }

    @Override
    @Transactional
    public Object deleteItemTemplatesItemId(Long itemId) {
        UbmpBenefitTmplItem item = benefitTmplItemMapper.selectById(itemId);
        if (item == null) {
            return ApiResponse.fail(404, "权益项模板不存在");
        }
        benefitTmplItemMapper.deleteById(item.getId());
        return ApiResponse.success("ok");
    }

    // ========== 跨租户多条件分页查询订阅 ==========

    @Override
    public Object getPlatformSubscriptions(Long appId,
                                           String userid,
                                           String setId,
                                           String status,
                                           String externalOrderId,
                                           String keyword,
                                           OffsetDateTime dateBeginStart,
                                           OffsetDateTime dateBeginEnd,
                                           OffsetDateTime createdAtStart,
                                           OffsetDateTime createdAtEnd,
                                           Integer page,
                                           Integer size) {
        if (appId != null && applicationMapper.selectById(appId) == null) {
            return ApiResponse.fail(404, "租户不存在");
        }
        LambdaQueryWrapper<UbmaSubscribe> query = new LambdaQueryWrapper<>();
        if (appId != null) query.eq(UbmaSubscribe::getAppId, appId);
        if (userid != null && !userid.isBlank()) query.eq(UbmaSubscribe::getUserid, userid.trim());
        if (setId != null && !setId.isBlank()) {
            Long sid = safeParseId(setId);
            if (sid == null) return ApiResponse.fail(400, "无效的权益集ID");
            query.eq(UbmaSubscribe::getSetId, sid);
        }
        if (status != null && !status.isBlank()) query.eq(UbmaSubscribe::getStatus, status.trim());
        if (externalOrderId != null && !externalOrderId.isBlank()) query.eq(UbmaSubscribe::getExternalOrderId, externalOrderId.trim());
        if (dateBeginStart != null) query.ge(UbmaSubscribe::getDateBegin, dateBeginStart);
        if (dateBeginEnd != null) query.le(UbmaSubscribe::getDateBegin, dateBeginEnd);
        if (createdAtStart != null) query.ge(UbmaSubscribe::getCreatedAt, createdAtStart);
        if (createdAtEnd != null) query.le(UbmaSubscribe::getCreatedAt, createdAtEnd);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            query.and(w -> w.like(UbmaSubscribe::getUserid, kw)
                    .or().like(UbmaSubscribe::getExternalOrderId, kw));
        }

        // 注意: selectCount 必须在 orderByDesc + last("LIMIT..") 之前调用,
        // 否则 MyBatis-Plus 会把这些附加到 count SQL, PostgreSQL 拒绝 (column must appear in GROUP BY).
        long total = subscribeMapper.selectCount(query);

        query.orderByDesc(UbmaSubscribe::getCreatedAt);
        int p = pageOr1(page);
        int s = sizeOr20(size);
        query.last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<UbmaSubscribe> rows = subscribeMapper.selectList(query);

        // 拉 set_name
        Set<Long> setIds = rows.stream().map(UbmaSubscribe::getSetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> setNameById = new HashMap<>();
        if (!setIds.isEmpty()) {
            for (UbmaBenefitSet b : benefitSetMapper.selectBatchIds(setIds)) {
                setNameById.put(b.getId(), b.getName());
            }
        }
        // 拉 app_name
        Set<Long> appIds = rows.stream().map(UbmaSubscribe::getAppId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> appNameById = new HashMap<>();
        if (!appIds.isEmpty()) {
            for (UbmaApplication a : applicationMapper.selectBatchIds(appIds)) {
                appNameById.put(a.getId(), a.getName());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (UbmaSubscribe sub : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subscribe_id", IdObfuscator.toOpenId(sub.getId()));
            row.put("app_id", IdObfuscator.toOpenId(sub.getAppId()));
            row.put("app_name", appNameById.getOrDefault(sub.getAppId(), ""));
            row.put("userid", sub.getUserid());
            row.put("set_id", IdObfuscator.toOpenId(sub.getSetId()));
            row.put("set_name", setNameById.getOrDefault(sub.getSetId(), ""));
            row.put("quota_limit", sub.getQuotaLimit());
            row.put("total_consumed", sub.getTotalConsumed());
            row.put("period_consumed", sub.getPeriodConsumed());
            row.put("frozen_consumed", sub.getFrozenConsumed());
            row.put("date_begin", sub.getDateBegin());
            row.put("date_end", sub.getDateEnd());
            row.put("status", sub.getStatus());
            row.put("external_order_id", sub.getExternalOrderId());
            row.put("created_at", sub.getCreatedAt());
            row.put("updated_at", sub.getUpdatedAt());
            list.add(row);
        }
        return ApiResponse.success(buildPage(list, total, p, s));
    }

    @Override
    public Object getPlatformSubscriptionsSubscribeIdItems(Long appId, String subscribeId, String itemId) {
        Long sid = safeParseId(subscribeId);
        if (sid == null) return ApiResponse.fail(400, "无效的订阅ID");

        UbmaSubscribe sub = subscribeMapper.selectById(sid);
        if (sub == null) return ApiResponse.fail(404, "订阅不存在");
        if (appId != null && !sub.getAppId().equals(appId)) {
            return ApiResponse.fail(404, "订阅不属于该租户");
        }

        LambdaQueryWrapper<UbmaSubscribeItem> q = new LambdaQueryWrapper<>();
        q.eq(UbmaSubscribeItem::getSubscribeId, sid);
        if (itemId != null && !itemId.isBlank()) {
            Long iid = safeParseId(itemId);
            if (iid == null) return ApiResponse.fail(400, "无效的权益项ID");
            q.eq(UbmaSubscribeItem::getItemId, iid);
        }
        q.orderByDesc(UbmaSubscribeItem::getBucketPriority)
                .orderByAsc(UbmaSubscribeItem::getCreatedAt);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(q);

        List<Map<String, Object>> list = new ArrayList<>();
        for (UbmaSubscribeItem it : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(it.getId()));
            row.put("subscribe_id", IdObfuscator.toOpenId(it.getSubscribeId()));
            row.put("item_id", IdObfuscator.toOpenId(it.getItemId()));
            row.put("quota_limit", it.getQuotaLimit());
            row.put("period_consumed", it.getPeriodConsumed());
            row.put("frozen_consumed", it.getFrozenConsumed());
            row.put("source_type", it.getSourceType());
            row.put("bucket_priority", it.getBucketPriority());
            row.put("expires_at", it.getExpiresAt());
            row.put("next_refresh_time", it.getNextRefreshTime());
            row.put("created_at", it.getCreatedAt());
            list.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", list.size());
        return ApiResponse.success(data);
    }

    // ========== 跨租户多条件分页查询扣减流水 ==========

    @Override
    public Object getPlatformConsumes(Long appId,
                                      String userid,
                                      String subsItemId,
                                      String itemId,
                                      String status,
                                      String externalOrderId,
                                      String keyword,
                                      Integer consumeNumMin,
                                      Integer consumeNumMax,
                                      OffsetDateTime consumeTimeStart,
                                      OffsetDateTime consumeTimeEnd,
                                      Integer page,
                                      Integer size) {
        if (appId != null && applicationMapper.selectById(appId) == null) {
            return ApiResponse.fail(404, "租户不存在");
        }
        Set<Long> subscribeItemIdScope = null;
        if (userid != null && !userid.isBlank()) {
            LambdaQueryWrapper<UbmaSubscribe> subQ = new LambdaQueryWrapper<>();
            subQ.eq(UbmaSubscribe::getUserid, userid.trim());
            if (appId != null) subQ.eq(UbmaSubscribe::getAppId, appId);
            List<UbmaSubscribe> subs = subscribeMapper.selectList(subQ);
            if (subs.isEmpty()) return ApiResponse.success(buildPage(java.util.Collections.emptyList(), 0, pageOr1(page), sizeOr20(size)));
            Set<Long> subIds = subs.stream().map(UbmaSubscribe::getId).collect(Collectors.toSet());
            LambdaQueryWrapper<UbmaSubscribeItem> itemQ = new LambdaQueryWrapper<>();
            itemQ.in(UbmaSubscribeItem::getSubscribeId, subIds);
            subscribeItemIdScope = subscribeItemMapper.selectList(itemQ).stream()
                    .map(UbmaSubscribeItem::getId).collect(Collectors.toSet());
            if (subscribeItemIdScope.isEmpty()) {
                return ApiResponse.success(buildPage(java.util.Collections.emptyList(), 0, pageOr1(page), sizeOr20(size)));
            }
        }

        LambdaQueryWrapper<UbmaConsume> query = new LambdaQueryWrapper<>();
        if (appId != null) query.eq(UbmaConsume::getAppId, appId);
        if (subscribeItemIdScope != null) {
            if (subsItemId != null && !subsItemId.isBlank()) {
                Long sid = safeParseId(subsItemId);
                if (sid == null) return ApiResponse.fail(400, "无效的订阅明细ID");
                if (!subscribeItemIdScope.contains(sid)) {
                    return ApiResponse.success(buildPage(java.util.Collections.emptyList(), 0, pageOr1(page), sizeOr20(size)));
                }
                query.eq(UbmaConsume::getSubsItemId, sid);
            } else {
                query.in(UbmaConsume::getSubsItemId, subscribeItemIdScope);
            }
        } else if (subsItemId != null && !subsItemId.isBlank()) {
            Long sid = safeParseId(subsItemId);
            if (sid == null) return ApiResponse.fail(400, "无效的订阅明细ID");
            query.eq(UbmaConsume::getSubsItemId, sid);
        }
        if (itemId != null && !itemId.isBlank()) {
            Long iid = safeParseId(itemId);
            if (iid == null) return ApiResponse.fail(400, "无效的权益项ID");
            query.eq(UbmaConsume::getItemId, iid);
        }
        if (status != null && !status.isBlank()) query.eq(UbmaConsume::getStatus, status.trim());
        if (externalOrderId != null && !externalOrderId.isBlank()) query.eq(UbmaConsume::getExternalOrderId, externalOrderId.trim());
        if (consumeNumMin != null) query.ge(UbmaConsume::getConsumeNum, consumeNumMin);
        if (consumeNumMax != null) query.le(UbmaConsume::getConsumeNum, consumeNumMax);
        if (consumeTimeStart != null) query.ge(UbmaConsume::getConsumeTime, consumeTimeStart);
        if (consumeTimeEnd != null) query.le(UbmaConsume::getConsumeTime, consumeTimeEnd);
        if (keyword != null && !keyword.isBlank()) {
            query.and(w -> w.like(UbmaConsume::getExternalOrderId, keyword.trim()));
        }

        // selectCount 必须在 orderByDesc + last("LIMIT..") 之前调用 (PostgreSQL 兼容).
        long total = consumeMapper.selectCount(query);

        query.orderByDesc(UbmaConsume::getConsumeTime);
        int p = pageOr1(page);
        int s = sizeOr20(size);
        query.last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<UbmaConsume> rows = consumeMapper.selectList(query);

        Set<Long> itemIds = rows.stream().map(UbmaConsume::getItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> itemNameById = new HashMap<>();
        if (!itemIds.isEmpty()) {
            for (UbmaBenefitItem i : benefitItemMapper.selectBatchIds(itemIds)) {
                itemNameById.put(i.getId(), i.getName());
            }
        }
        Set<Long> subsItemIds = rows.stream().map(UbmaConsume::getSubsItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> useridBySubsItemId = new HashMap<>();
        if (!subsItemIds.isEmpty()) {
            List<UbmaSubscribeItem> siList = subscribeItemMapper.selectBatchIds(subsItemIds);
            Set<Long> subIds = siList.stream().map(UbmaSubscribeItem::getSubscribeId).collect(Collectors.toSet());
            Map<Long, String> useridBySubId = new HashMap<>();
            if (!subIds.isEmpty()) {
                for (UbmaSubscribe sub : subscribeMapper.selectBatchIds(subIds)) {
                    useridBySubId.put(sub.getId(), sub.getUserid());
                }
            }
            for (UbmaSubscribeItem si : siList) {
                useridBySubsItemId.put(si.getId(), useridBySubId.getOrDefault(si.getSubscribeId(), ""));
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (UbmaConsume c : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("consume_id", IdObfuscator.toOpenId(c.getId()));
            row.put("app_id", IdObfuscator.toOpenId(c.getAppId()));
            row.put("userid", useridBySubsItemId.getOrDefault(c.getSubsItemId(), ""));
            row.put("subs_item_id", IdObfuscator.toOpenId(c.getSubsItemId()));
            row.put("item_id", IdObfuscator.toOpenId(c.getItemId()));
            row.put("item_name", itemNameById.getOrDefault(c.getItemId(), ""));
            row.put("external_order_id", c.getExternalOrderId());
            row.put("consume_num", c.getConsumeNum());
            row.put("status", c.getStatus());
            row.put("consume_time", c.getConsumeTime());
            row.put("expire_time", c.getExpireTime());
            row.put("refundable", "COMMITTED".equals(c.getStatus()) && c.getConsumeNum() != null && c.getConsumeNum() > 0);
            row.put("ext", c.getExt());
            row.put("created_at", c.getCreatedAt());
            list.add(row);
        }
        return ApiResponse.success(buildPage(list, total, p, s));
    }

    // ========== 跨租户手动退减 ==========

    @Override
    @Transactional
    public Object postPlatformConsumesIdRefund(Long appId, String consumeId, PostConsumesIdRefundRequest req) {
        if (appId != null && applicationMapper.selectById(appId) == null) {
            return ApiResponse.fail(404, "租户不存在");
        }
        return doPlatformRefundConsume(appId, consumeId, req);
    }

    private Object doPlatformRefundConsume(Long appId, String consumeId, PostConsumesIdRefundRequest req) {
        Long id = safeParseId(consumeId);
        if (id == null) return ApiResponse.fail(400, "无效的消费流水ID");
        UbmaConsume consume = consumeMapper.selectById(id);
        if (consume == null) return ApiResponse.fail(404, "扣减流水不存在");
        if (appId != null && !appId.equals(consume.getAppId())) {
            return ApiResponse.fail(404, "扣减流水不存在");
        }
        if (!"COMMITTED".equals(consume.getStatus())) {
            return ApiResponse.fail(400, "仅 COMMITTED 状态可退减, 当前=" + consume.getStatus());
        }
        Integer num = consume.getConsumeNum();
        if (num == null || num <= 0) return ApiResponse.fail(400, "扣减数量为0, 无需退减");

        UbmaSubscribeItem item = subscribeItemMapper.selectById(consume.getSubsItemId());
        if (item == null) return ApiResponse.fail(500, "订阅明细不存在, 账本数据不一致");
        int newTotal = Math.max(0, safeInt(item.getTotalConsumed()) - num);
        int newPeriod = Math.max(0, safeInt(item.getPeriodConsumed()) - num);
        LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                .set(UbmaSubscribeItem::getTotalConsumed, newTotal)
                .set(UbmaSubscribeItem::getPeriodConsumed, newPeriod)
                .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
        if (subscribeItemMapper.update(null, itemWrapper) == 0) {
            return ApiResponse.fail(409, "并发冲突, 请重试");
        }

        UbmaSubscribe sub = subscribeMapper.selectById(item.getSubscribeId());
        if (sub != null) {
            int subNewTotal = Math.max(0, safeInt(sub.getTotalConsumed()) - num);
            int subNewPeriod = Math.max(0, safeInt(sub.getPeriodConsumed()) - num);
            LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
            subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, sub.getVersion())
                    .set(UbmaSubscribe::getTotalConsumed, subNewTotal)
                    .set(UbmaSubscribe::getPeriodConsumed, subNewPeriod)
                    .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, subWrapper);
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, Object> ext = new LinkedHashMap<>();
        if (consume.getExt() instanceof Map) {
            ext.putAll((Map<String, Object>) consume.getExt());
        }
        ext.put("refund_reason", req != null ? req.getReason() : null);
        ext.put("refund_operator", req != null ? req.getOperator() : null);
        ext.put("refund_at", now.toString());
        LambdaUpdateWrapper<UbmaConsume> consWrapper = new LambdaUpdateWrapper<>();
        consWrapper.eq(UbmaConsume::getId, consume.getId())
                .set(UbmaConsume::getStatus, "REFUNDED")
                .set(UbmaConsume::getExt, ext)
                .set(UbmaConsume::getUpdatedAt, now);
        consumeMapper.update(null, consWrapper);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consume_id", IdObfuscator.toOpenId(consume.getId()));
        result.put("status", "REFUNDED");
        result.put("refund_amount", num);
        result.put("refund_at", now);
        result.put("subs_item_id", IdObfuscator.toOpenId(item.getId()));
        result.put("subs_item_total_after", newTotal);
        result.put("subs_item_period_after", newPeriod);
        return ApiResponse.success(result);
    }

    private int safeInt(Integer val) {
        return val != null ? val : 0;
    }

    private int pageOr1(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int sizeOr20(Integer size) {
        if (size == null || size < 1) return 20;
        return Math.min(size, 200);
    }

    private Map<String, Object> buildPage(List<Map<String, Object>> list, long total, int page, int size) {
        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("list", list);
        pageData.put("total", total);
        pageData.put("page", page);
        pageData.put("size", size);
        return pageData;
    }
}