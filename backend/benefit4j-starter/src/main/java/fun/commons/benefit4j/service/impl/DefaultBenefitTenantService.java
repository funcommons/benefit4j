package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.benefit4j.service.BenefitTenantService;
import fun.commons.framework4j.audit.annotation.Auditable;
import fun.commons.framework4j.cache.annotation.CacheableEvict;
import fun.commons.framework4j.cache.annotation.CacheableGet;
import fun.commons.framework4j.id.util.IdObfuscator;
import fun.commons.framework4j.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DefaultBenefitTenantService implements BenefitTenantService {

    private final UbmaCompensationMapper compensationMapper;
    private final UbmaSubscribeMapper subscribeMapper;
    private final UbmaSubscribeItemMapper subscribeItemMapper;
    private final UbmaBenefitItemMapper benefitItemMapper;
    private final UbmaBenefitSetMapper benefitSetMapper;
    private final UbmaBenefitRefMapper benefitRefMapper;
    private final UbmpBenefitTmplSetMapper benefitTmplSetMapper;
    private final UbmpBenefitTmplRefMapper benefitTmplRefMapper;
    private final UbmaConsumeMapper consumeMapper;

    @Override
    @Transactional
    public Object postBenefitItems(Long tenantId, PostBenefitItemsRequest req) {
        // 名称唯一性校验：同一租户下权益项名称不可重复
        if (req.getName() != null) {
            LambdaQueryWrapper<UbmaBenefitItem> nameQuery = new LambdaQueryWrapper<>();
            nameQuery.eq(UbmaBenefitItem::getTenantId, tenantId)
                    .eq(UbmaBenefitItem::getName, req.getName());
            if (benefitItemMapper.selectCount(nameQuery) > 0) {
                return ApiResponse.fail(409, "权益项名称已存在");
            }
        }

        UbmaBenefitItem item = new UbmaBenefitItem();
        item.setTenantId(tenantId);
        item.setName(req.getName());
        item.setIcon(req.getIcon());
        item.setDescription(req.getDescription());
        item.setDefaultDeduction(req.getDefaultDeduction() != null ? req.getDefaultDeduction() : 1);
        item.setStatus(req.getStatus() != null ? req.getStatus() : "ACTIVE");
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());
        benefitItemMapper.insert(item);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item_id", IdObfuscator.toOpenId(item.getId()));
        result.put("name", item.getName());
        result.put("status", item.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    public Object getBenefitItems(Long tenantId) {
        LambdaQueryWrapper<UbmaBenefitItem> query = new LambdaQueryWrapper<>();
        query.eq(UbmaBenefitItem::getTenantId, tenantId)
                .orderByDesc(UbmaBenefitItem::getCreatedAt);
        List<UbmaBenefitItem> items = benefitItemMapper.selectList(query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UbmaBenefitItem item : items) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("item_id", IdObfuscator.toOpenId(item.getId()));
            row.put("tenant_id", IdObfuscator.toOpenId(item.getTenantId()));
            row.put("name", item.getName());
            row.put("icon", item.getIcon());
            row.put("description", item.getDescription());
            row.put("default_deduction", item.getDefaultDeduction());
            row.put("status", item.getStatus());
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @Override
@CacheableGet(prefix = "benefit:item", key = "#tenantId+':'+#itemId", ttl = 600, nullTtl = 30)
    public Object getBenefitItemsItemId(Long tenantId, String itemId) {
        Long id = safeParseId(itemId);
        if (id == null) return ApiResponse.fail(400, "无效的权益项ID");
        UbmaBenefitItem item = benefitItemMapper.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益项不存在");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("item_id", IdObfuscator.toOpenId(item.getId()));
        result.put("tenant_id", IdObfuscator.toOpenId(item.getTenantId()));
        result.put("name", item.getName());
        result.put("icon", item.getIcon());
        result.put("description", item.getDescription());
        result.put("default_deduction", item.getDefaultDeduction());
        result.put("status", item.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
@CacheableEvict(prefix = "benefit:item", key = "#tenantId+':'+#itemId")
    public Object putBenefitItemsItemId(Long tenantId, String itemId, PutBenefitItemsItemIdRequest req) {
        Long id = safeParseId(itemId);
        if (id == null) return ApiResponse.fail(400, "无效的权益项ID");
        UbmaBenefitItem item = benefitItemMapper.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益项不存在");
        }

        LambdaUpdateWrapper<UbmaBenefitItem> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaBenefitItem::getId, item.getId());
        if (req.getName() != null) wrapper.set(UbmaBenefitItem::getName, req.getName());
        if (req.getIcon() != null) wrapper.set(UbmaBenefitItem::getIcon, req.getIcon());
        if (req.getDescription() != null) wrapper.set(UbmaBenefitItem::getDescription, req.getDescription());
        if (req.getDefaultDeduction() != null) wrapper.set(UbmaBenefitItem::getDefaultDeduction, req.getDefaultDeduction());
        if (req.getStatus() != null) wrapper.set(UbmaBenefitItem::getStatus, req.getStatus());
        wrapper.set(UbmaBenefitItem::getUpdatedAt, OffsetDateTime.now());
        benefitItemMapper.update(null, wrapper);

        return ApiResponse.success("ok");
    }

    @Override
    @Transactional
@CacheableEvict(prefix = "benefit:item", key = "#tenantId+':'+#itemId")
    public Object deleteBenefitItemsItemId(Long tenantId, String itemId) {
        Long id = safeParseId(itemId);
        if (id == null) return ApiResponse.fail(400, "无效的权益项ID");
        UbmaBenefitItem item = benefitItemMapper.selectById(id);
        if (item == null || !item.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益项不存在");
        }

        // 检查是否有活跃订阅引用
        LambdaQueryWrapper<UbmaSubscribeItem> refCheck = new LambdaQueryWrapper<>();
        refCheck.eq(UbmaSubscribeItem::getItemId, item.getId());
        if (subscribeItemMapper.selectCount(refCheck) > 0) {
            return ApiResponse.fail(400, "该权益项存在订阅引用，无法删除");
        }

        benefitItemMapper.deleteById(item.getId());
        return ApiResponse.success("ok");
    }

    @Override
    public Object getBenefitTemplates(Long tenantId) {
        // 租户可见模板 = 所有 ACTIVE 平台公共模板
        // TODO: 当租户-模板授权表建立后，增加授权过滤
        LambdaQueryWrapper<UbmpBenefitTmplSet> query = new LambdaQueryWrapper<>();
        query.eq(UbmpBenefitTmplSet::getStatus, "ACTIVE");
        List<UbmpBenefitTmplSet> templates = benefitTmplSetMapper.selectList(query);
        Map<Long, List<Map<String, Object>>> refsByTmpl = new HashMap<>();
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
        List<Map<String, Object>> result = new ArrayList<>();
        for (UbmpBenefitTmplSet t : templates) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", IdObfuscator.toOpenId(t.getId()));
            row.put("name", t.getName());
            row.put("duration", t.getDuration());
            row.put("duration_unit", t.getDurationUnit());
            row.put("priority", t.getPriority());
            row.put("quota", t.getQuota());
            row.put("refresh_cycle", t.getRefreshCycle());
            row.put("refresh_cycle_unit", t.getRefreshCycleUnit());
            row.put("status", t.getStatus());
            row.put("refs", refsByTmpl.getOrDefault(t.getId(), Collections.emptyList()));
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object postBenefitSets(Long tenantId, PostBenefitSetsRequest req) {
        OffsetDateTime now = OffsetDateTime.now();

        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setTenantId(tenantId);
        set.setName(req.getName());
        set.setDuration(req.getDuration());
        set.setDurationUnit(req.getDurationUnit());
        set.setTimingMode(req.getTimingMode() != null ? req.getTimingMode() : "RENEWAL");
        set.setQuota(req.getQuota() != null ? req.getQuota() : 0);
        set.setQuotaUnit(req.getQuotaUnit() != null ? req.getQuotaUnit() : "次");
        set.setRefreshCycle(req.getRefreshCycle());
        set.setRefreshCycleUnit(req.getRefreshCycleUnit());
        set.setPriority(req.getPriority() != null ? req.getPriority() : 0);
        set.setStatus("ACTIVE");
        set.setExt(req.getExt());
        set.setCreatedAt(now);
        set.setUpdatedAt(now);
        benefitSetMapper.insert(set);

        for (PostBenefitSetsRequest.BenefitSetItemRef itemRef : req.getItems()) {
            Long refItemId = safeParseId(itemRef.getItemId());
            if (refItemId == null) return ApiResponse.fail(400, "无效的权益项ID");
            UbmaBenefitItem refItem = benefitItemMapper.selectById(refItemId);
            if (refItem == null || !refItem.getTenantId().equals(tenantId)) {
                return ApiResponse.fail(404, "权益项不存在: " + itemRef.getItemId());
            }
            UbmaBenefitRef ref = new UbmaBenefitRef();
            ref.setTenantId(tenantId);
            ref.setSetId(set.getId());
            ref.setItemId(refItemId);
            ref.setQuota(itemRef.getQuota() != null ? itemRef.getQuota() : 0);
            ref.setRefreshCycle(itemRef.getRefreshCycle());
            ref.setRefreshCycleUnit(itemRef.getRefreshCycleUnit());
            ref.setCreatedAt(now);
            ref.setUpdatedAt(now);
            benefitRefMapper.insert(ref);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("set_id", IdObfuscator.toOpenId(set.getId()));
        result.put("name", set.getName());
        result.put("status", set.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    public Object getBenefitSets(Long tenantId) {
        LambdaQueryWrapper<UbmaBenefitSet> query = new LambdaQueryWrapper<>();
        query.eq(UbmaBenefitSet::getTenantId, tenantId)
                .orderByDesc(UbmaBenefitSet::getCreatedAt);
        List<UbmaBenefitSet> sets = benefitSetMapper.selectList(query);
        List<Map<String, Object>> result = new ArrayList<>();
        for (UbmaBenefitSet set : sets) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("set_id", IdObfuscator.toOpenId(set.getId()));
            row.put("tenant_id", IdObfuscator.toOpenId(set.getTenantId()));
            row.put("name", set.getName());
            row.put("duration", set.getDuration());
            row.put("duration_unit", set.getDurationUnit());
            row.put("priority", set.getPriority());
            row.put("quota", set.getQuota());
            row.put("refresh_cycle", set.getRefreshCycle());
            row.put("refresh_cycle_unit", set.getRefreshCycleUnit());
            row.put("timing_mode", set.getTimingMode());
            row.put("quota_unit", set.getQuotaUnit());
            row.put("status", set.getStatus());
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @Override
@CacheableGet(prefix = "benefit:set", key = "#tenantId+':'+#setId", ttl = 600, nullTtl = 30)
    public Object getBenefitSetsSetId(Long tenantId, String setId) {
        Long id = safeParseId(setId);
        if (id == null) return ApiResponse.fail(400, "无效的权益集ID");
        UbmaBenefitSet set = benefitSetMapper.selectById(id);
        if (set == null || !set.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益集不存在");
        }

        LambdaQueryWrapper<UbmaBenefitRef> refQuery = new LambdaQueryWrapper<>();
        refQuery.eq(UbmaBenefitRef::getSetId, set.getId());
        List<UbmaBenefitRef> refs = benefitRefMapper.selectList(refQuery);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("set_id", IdObfuscator.toOpenId(set.getId()));
        result.put("name", set.getName());
        result.put("duration", set.getDuration());
        result.put("duration_unit", set.getDurationUnit());
        result.put("quota", set.getQuota());
        result.put("priority", set.getPriority());
        result.put("status", set.getStatus());

        List<Map<String, Object>> refList = new java.util.ArrayList<>();
        for (UbmaBenefitRef ref : refs) {
            Map<String, Object> refMap = new LinkedHashMap<>();
            refMap.put("ref_id", IdObfuscator.toOpenId(ref.getId()));
            refMap.put("item_id", IdObfuscator.toOpenId(ref.getItemId()));
            refMap.put("quota", ref.getQuota());
            refMap.put("refresh_cycle", ref.getRefreshCycle());
            refMap.put("refresh_cycle_unit", ref.getRefreshCycleUnit());
            refList.add(refMap);
        }
        result.put("items", refList);
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
@CacheableEvict(prefix = "benefit:set", key = "#tenantId+':'+#setId")
    public Object putBenefitSetsSetId(Long tenantId, String setId, PutBenefitSetsSetIdRequest req) {
        Long id = safeParseId(setId);
        if (id == null) return ApiResponse.fail(400, "无效的权益集ID");
        UbmaBenefitSet set = benefitSetMapper.selectById(id);
        if (set == null || !set.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益集不存在");
        }

        LambdaUpdateWrapper<UbmaBenefitSet> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaBenefitSet::getId, set.getId());
        if (req.getName() != null) wrapper.set(UbmaBenefitSet::getName, req.getName());
        if (req.getDuration() != null) wrapper.set(UbmaBenefitSet::getDuration, req.getDuration());
        if (req.getDurationUnit() != null) wrapper.set(UbmaBenefitSet::getDurationUnit, req.getDurationUnit());
        if (req.getTimingMode() != null) wrapper.set(UbmaBenefitSet::getTimingMode, req.getTimingMode());
        if (req.getQuota() != null) wrapper.set(UbmaBenefitSet::getQuota, req.getQuota());
        if (req.getQuotaUnit() != null) wrapper.set(UbmaBenefitSet::getQuotaUnit, req.getQuotaUnit());
        if (req.getRefreshCycle() != null) wrapper.set(UbmaBenefitSet::getRefreshCycle, req.getRefreshCycle());
        if (req.getRefreshCycleUnit() != null) wrapper.set(UbmaBenefitSet::getRefreshCycleUnit, req.getRefreshCycleUnit());
        if (req.getPriority() != null) wrapper.set(UbmaBenefitSet::getPriority, req.getPriority());
        if (req.getExt() != null) wrapper.set(UbmaBenefitSet::getExt, req.getExt());
        wrapper.set(UbmaBenefitSet::getUpdatedAt, OffsetDateTime.now());
        benefitSetMapper.update(null, wrapper);

        if (req.getItems() != null) {
            LambdaQueryWrapper<UbmaBenefitRef> deleteQuery = new LambdaQueryWrapper<>();
            deleteQuery.eq(UbmaBenefitRef::getSetId, set.getId());
            benefitRefMapper.delete(deleteQuery);

            for (PostBenefitSetsRequest.BenefitSetItemRef itemRef : req.getItems()) {
                Long refItemId = safeParseId(itemRef.getItemId());
                if (refItemId == null) return ApiResponse.fail(400, "无效的权益项ID");
                UbmaBenefitItem refItem = benefitItemMapper.selectById(refItemId);
                if (refItem == null || !refItem.getTenantId().equals(tenantId)) {
                    return ApiResponse.fail(404, "权益项不存在: " + itemRef.getItemId());
                }
                UbmaBenefitRef ref = new UbmaBenefitRef();
                ref.setTenantId(tenantId);
                ref.setSetId(set.getId());
                ref.setItemId(refItemId);
                ref.setQuota(itemRef.getQuota() != null ? itemRef.getQuota() : 0);
                ref.setRefreshCycle(itemRef.getRefreshCycle());
                ref.setRefreshCycleUnit(itemRef.getRefreshCycleUnit());
                ref.setCreatedAt(OffsetDateTime.now());
                ref.setUpdatedAt(OffsetDateTime.now());
                benefitRefMapper.insert(ref);
            }
        }

        return ApiResponse.success("ok");
    }

    @Override
    @Transactional
@CacheableEvict(prefix = "benefit:set", key = "#tenantId+':'+#setId")
    public Object deleteBenefitSetsSetId(Long tenantId, String setId) {
        Long id = safeParseId(setId);
        if (id == null) return ApiResponse.fail(400, "无效的权益集ID");
        UbmaBenefitSet set = benefitSetMapper.selectById(id);
        if (set == null || !set.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益集不存在");
        }

        // 检查是否有活跃订阅引用
        LambdaQueryWrapper<UbmaSubscribe> subCheck = new LambdaQueryWrapper<>();
        subCheck.eq(UbmaSubscribe::getSetId, set.getId())
                .in(UbmaSubscribe::getStatus, "ACTIVE", "EXHAUSTED");
        if (subscribeMapper.selectCount(subCheck) > 0) {
            return ApiResponse.fail(400, "该权益集存在活跃订阅，无法删除");
        }

        benefitSetMapper.deleteById(set.getId());

        // Cascade delete refs
        LambdaQueryWrapper<UbmaBenefitRef> refDeleteQuery = new LambdaQueryWrapper<>();
        refDeleteQuery.eq(UbmaBenefitRef::getSetId, set.getId());
        benefitRefMapper.delete(refDeleteQuery);

        return ApiResponse.success("ok");
    }

    @Override
    public Object getUsersUseridAssets(Long tenantId, String userid) {
        LambdaQueryWrapper<UbmaSubscribe> query = new LambdaQueryWrapper<>();
        query.eq(UbmaSubscribe::getTenantId, tenantId)
                .eq(UbmaSubscribe::getUserid, userid)
                .orderByDesc(UbmaSubscribe::getCreatedAt);
        List<UbmaSubscribe> subscribes = subscribeMapper.selectList(query);

        List<Map<String, Object>> assets = new ArrayList<>();
        for (UbmaSubscribe sub : subscribes) {
            LambdaQueryWrapper<UbmaSubscribeItem> itemQuery = new LambdaQueryWrapper<>();
            itemQuery.eq(UbmaSubscribeItem::getSubscribeId, sub.getId());
            List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(itemQuery);
            assets.add(buildSubscribeDetail(sub, items));
        }
        return ApiResponse.success(assets);
    }

    @Override
    public Object getUsersUseridConsumes(Long tenantId, String userid) {
        LambdaQueryWrapper<UbmaSubscribe> subQuery = new LambdaQueryWrapper<>();
        subQuery.eq(UbmaSubscribe::getTenantId, tenantId)
                .eq(UbmaSubscribe::getUserid, userid);
        List<UbmaSubscribe> subscribes = subscribeMapper.selectList(subQuery);

        if (subscribes.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        Set<Long> subscribeIds = subscribes.stream().map(UbmaSubscribe::getId).collect(Collectors.toSet());

        LambdaQueryWrapper<UbmaSubscribeItem> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.in(UbmaSubscribeItem::getSubscribeId, subscribeIds);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(itemQuery);

        if (items.isEmpty()) {
            return ApiResponse.success(Collections.emptyList());
        }

        Set<Long> subsItemIds = items.stream().map(UbmaSubscribeItem::getId).collect(Collectors.toSet());

        // Consume records are in the runtime service's mapper; return item-level summary
        List<Map<String, Object>> result = new ArrayList<>();
        for (UbmaSubscribeItem item : items) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("subs_item_id", IdObfuscator.toOpenId(item.getId()));
            map.put("item_id", IdObfuscator.toOpenId(item.getItemId()));
            map.put("quota_limit", item.getQuotaLimit());
            map.put("total_consumed", item.getTotalConsumed());
            map.put("period_consumed", item.getPeriodConsumed());
            map.put("frozen_consumed", item.getFrozenConsumed());
            result.add(map);
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
@Auditable(action = "SUBSCRIPTION_CREATE", targetType = "subscribe",
            targetIdSpel = "#req.externalOrderId")
    public Object postSubscriptions(Long tenantId, PostSubscriptionsRequest req) {
        // Reuse the same logic as runtime postSubscriptions but with tenantId as tenantId
        LambdaQueryWrapper<UbmaSubscribe> idempotentQuery = new LambdaQueryWrapper<>();
        idempotentQuery.eq(UbmaSubscribe::getTenantId, tenantId)
                .eq(UbmaSubscribe::getExternalOrderId, req.getExternalOrderId());
        UbmaSubscribe existing = subscribeMapper.selectOne(idempotentQuery);
        if (existing != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("subscribe_id", IdObfuscator.toOpenId(existing.getId()));
            result.put("date_begin", existing.getDateBegin());
            result.put("date_end", existing.getDateEnd());
            result.put("status", existing.getStatus());
            return ApiResponse.success(result);
        }

        Long setId = safeParseId(req.getSetId());
        if (setId == null) return ApiResponse.fail(400, "无效的权益集ID");
        UbmaBenefitSet set = benefitSetMapper.selectById(setId);
        if (set == null || !set.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "权益集不存在");
        }
        if (!"ACTIVE".equals(set.getStatus())) {
            return ApiResponse.fail(400, "权益集已停用");
        }

        LambdaQueryWrapper<UbmaBenefitRef> refQuery = new LambdaQueryWrapper<>();
        refQuery.eq(UbmaBenefitRef::getTenantId, tenantId)
                .eq(UbmaBenefitRef::getSetId, set.getId());
        List<UbmaBenefitRef> refs = benefitRefMapper.selectList(refQuery);
        if (refs.isEmpty()) {
            return ApiResponse.fail(400, "权益集没有配置任何权益项");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dateEnd = calculateDateEnd(now, set.getDuration(), set.getDurationUnit());
        OffsetDateTime nextRefreshTime = calculateNextRefreshTime(now, set.getRefreshCycle(), set.getRefreshCycleUnit());

        UbmaSubscribe subscribe = new UbmaSubscribe();
        subscribe.setTenantId(tenantId);
        subscribe.setUserid(req.getUserid());
        subscribe.setSetId(set.getId());
        subscribe.setExternalOrderId(req.getExternalOrderId());
        subscribe.setTotalConsumed(0);
        subscribe.setPeriodConsumed(0);
        subscribe.setFrozenConsumed(0);
        subscribe.setQuotaLimit(set.getQuota());
        subscribe.setNextRefreshTime(nextRefreshTime);
        subscribe.setDateBegin(now);
        subscribe.setDateEnd(dateEnd);
        subscribe.setStatus("ACTIVE");
        subscribe.setExt(req.getExt());
        subscribe.setCreatedAt(now);
        subscribe.setUpdatedAt(now);
        subscribeMapper.insert(subscribe);

        for (UbmaBenefitRef ref : refs) {
            UbmaSubscribeItem item = new UbmaSubscribeItem();
            item.setTenantId(tenantId);
            item.setSubscribeId(subscribe.getId());
            item.setItemId(ref.getItemId());
            item.setTotalConsumed(0);
            item.setPeriodConsumed(0);
            item.setFrozenConsumed(0);
            item.setQuotaLimit(ref.getQuota());
            item.setNextRefreshTime(calculateNextRefreshTime(now, ref.getRefreshCycle(), ref.getRefreshCycleUnit()));
            // V1.2.0 多源桶: 桶字段与 Runtime 端 postSubscriptions 对齐
            // source_type 默认 SUBSCRIPTION (后续运营可扩展为 TOPUP 等)
            // bucket_priority 继承 set.priority, 数字越大越优先扣减
            // expires_at 跟随 subscribe 整体过期 (per-bucket 可由后续补偿/迁移覆盖)
            item.setSourceType("SUBSCRIPTION");
            item.setBucketPriority(set.getPriority() != null ? set.getPriority() : 0);
            item.setExpiresAt(dateEnd);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            subscribeItemMapper.insert(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribe_id", IdObfuscator.toOpenId(subscribe.getId()));
        result.put("date_begin", subscribe.getDateBegin());
        result.put("date_end", subscribe.getDateEnd());
        result.put("status", subscribe.getStatus());
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
@Auditable(action = "SUBSCRIPTION_DISABLE", targetType = "subscribe",
            targetIdSpel = "#subscribeId")
    public Object postSubscriptionsSubscribeIdDisable(Long tenantId, String subscribeId, PostSubscriptionsSubscribeIdDisableRequest req) {
        Long id = safeParseId(subscribeId);
        if (id == null) return ApiResponse.fail(400, "无效的订阅ID");
        UbmaSubscribe subscribe = subscribeMapper.selectById(id);
        if (subscribe == null || !subscribe.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }
        if (!"ACTIVE".equals(subscribe.getStatus()) && !"EXHAUSTED".equals(subscribe.getStatus())) {
            return ApiResponse.fail(400, "订阅状态不允许禁用");
        }
        if (subscribe.getFrozenConsumed() != null && subscribe.getFrozenConsumed() > 0) {
            return ApiResponse.fail(400, "订阅存在冻结额度，请先释放后再禁用");
        }

        subscribe = subscribeMapper.selectById(subscribe.getId()); // re-read for version
        LambdaUpdateWrapper<UbmaSubscribe> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaSubscribe::getId, subscribe.getId())
                .eq(UbmaSubscribe::getVersion, subscribe.getVersion())
                .set(UbmaSubscribe::getStatus, "DISABLED")
                .set(UbmaSubscribe::getVersion, subscribe.getVersion() + 1)
                .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
        int rows = subscribeMapper.update(null, wrapper);
        if (rows == 0) {
            return ApiResponse.fail(409, "并发冲突，请重试");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribe_id", IdObfuscator.toOpenId(subscribe.getId()));
        result.put("status", "DISABLED");
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
@Auditable(action = "COMPENSATION", targetType = "compensation",
            targetIdSpel = "#req.subscribeId + ':' + #req.subsItemId")
    public Object postCompensations(Long tenantId, PostCompensationsRequest req) {
        Long subId = safeParseId(req.getSubscribeId());
        if (subId == null) return ApiResponse.fail(400, "无效的订阅ID");
        UbmaSubscribe sub = subscribeMapper.selectById(subId);
        if (sub == null || !sub.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }
        if (!"ACTIVE".equals(sub.getStatus()) && !"EXHAUSTED".equals(sub.getStatus())) {
            return ApiResponse.fail(400, "订阅状态不允许调整");
        }

        Long itemId = safeParseId(req.getSubsItemId());
        if (itemId == null) return ApiResponse.fail(400, "无效的订阅明细ID");
        UbmaSubscribeItem item = subscribeItemMapper.selectById(itemId);
        if (item == null || !item.getSubscribeId().equals(sub.getId())) {
            return ApiResponse.fail(404, "订阅明细不存在");
        }

        int adjustNum = req.getAdjustNum() != null ? req.getAdjustNum() : 1;
        if (adjustNum <= 0) return ApiResponse.fail(400, "调整数量必须大于0");
        String adjustType = req.getAdjustType() != null ? req.getAdjustType() : "ADD";

        Long compItemId = safeParseId(req.getItemId());
        if (compItemId == null) return ApiResponse.fail(400, "无效的权益项ID");

        item = subscribeItemMapper.selectById(item.getId());
        UbmaSubscribeItem auditSubsItem = item; // 审计行记录: ADD 指向新桶, REDUCE 指向原桶
        if ("ADD".equals(adjustType)) {
            // ADD: 创建新桶 (而非在原桶上累加), 让独立充值/补偿/赠送可与月度赠送共存
            UbmaSubscribeItem newBucket = new UbmaSubscribeItem();
            newBucket.setTenantId(tenantId);
            newBucket.setSubscribeId(sub.getId());
            newBucket.setItemId(compItemId);
            newBucket.setTotalConsumed(0);
            newBucket.setPeriodConsumed(0);
            newBucket.setFrozenConsumed(0);
            newBucket.setQuotaLimit(adjustNum);
            newBucket.setNextRefreshTime(null);
            newBucket.setSourceType(req.getSourceType() != null ? req.getSourceType() : "COMPENSATION");
            newBucket.setBucketPriority(req.getPriority() != null ? req.getPriority() : 0);
            newBucket.setExpiresAt(req.getExpiresAt());
            newBucket.setCreatedAt(OffsetDateTime.now());
            newBucket.setUpdatedAt(OffsetDateTime.now());
            subscribeItemMapper.insert(newBucket);
            auditSubsItem = newBucket;
        } else {
            // REDUCE: 在原桶上扣减 (带乐观锁 + 已消费校验)
            LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
            itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, item.getVersion());
            if (item.getQuotaLimit() - adjustNum < 0) {
                return ApiResponse.fail(400, "减少额度后不能小于0");
            }
            int consumed = (item.getPeriodConsumed() != null ? item.getPeriodConsumed() : 0)
                    + (item.getFrozenConsumed() != null ? item.getFrozenConsumed() : 0);
            if (item.getQuotaLimit() - adjustNum < consumed) {
                return ApiResponse.fail(400, "减少额度后不能小于已消费额度");
            }
            itemWrapper.set(UbmaSubscribeItem::getQuotaLimit, item.getQuotaLimit() - adjustNum)
                    .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                    .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
            int itemRows = subscribeItemMapper.update(null, itemWrapper);
            if (itemRows == 0) {
                return ApiResponse.fail(409, "并发冲突，请重试");
            }
        }

        // 同步更新父订阅 quotaLimit, 保持 EXHAUSTED 检测一致 (set 总额 + 累计补偿)
        sub = subscribeMapper.selectById(sub.getId());
        LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
        subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                .eq(UbmaSubscribe::getVersion, sub.getVersion());
        if ("ADD".equals(adjustType)) {
            subWrapper.set(UbmaSubscribe::getQuotaLimit, sub.getQuotaLimit() + adjustNum);
        } else {
            if (sub.getQuotaLimit() - adjustNum < 0) {
                return ApiResponse.fail(400, "减少额度后不能小于0");
            }
            int subConsumed = safeInt(sub.getPeriodConsumed()) + safeInt(sub.getFrozenConsumed());
            if (sub.getQuotaLimit() - adjustNum < subConsumed) {
                return ApiResponse.fail(400, "减少额度后不能小于已消费额度");
            }
            subWrapper.set(UbmaSubscribe::getQuotaLimit, sub.getQuotaLimit() - adjustNum);
        }
        subWrapper.set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
        int subRows = subscribeMapper.update(null, subWrapper);
        if (subRows == 0) {
            return ApiResponse.fail(409, "并发冲突，请重试");
        }

        UbmaCompensation comp = new UbmaCompensation();
        comp.setTenantId(tenantId);
        comp.setSubscribeId(sub.getId());
        comp.setSubsItemId(auditSubsItem.getId());
        comp.setItemId(compItemId);
        comp.setAdjustNum(adjustNum);
        comp.setAdjustType(adjustType);
        comp.setReason(req.getReason());
        comp.setOperator(req.getOperator());
        comp.setCreatedAt(OffsetDateTime.now());
        comp.setUpdatedAt(OffsetDateTime.now());
        compensationMapper.insert(comp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("compensation_id", IdObfuscator.toOpenId(comp.getId()));
        result.put("subscribe_id", IdObfuscator.toOpenId(sub.getId()));
        result.put("subs_item_id", IdObfuscator.toOpenId(auditSubsItem.getId()));
        result.put("adjust_num", adjustNum);
        result.put("adjust_type", adjustType);
        if ("ADD".equals(adjustType)) {
            result.put("source_type", auditSubsItem.getSourceType());
            result.put("bucket_priority", auditSubsItem.getBucketPriority());
            result.put("expires_at", auditSubsItem.getExpiresAt());
        }
        return ApiResponse.success(result);
    }

    // ========== 订阅多条件分页查询 (租户) ==========

    @Override
    public Object getSubscriptions(Long tenantId,
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
        LambdaQueryWrapper<UbmaSubscribe> query = new LambdaQueryWrapper<>();
        query.eq(UbmaSubscribe::getTenantId, tenantId);
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
                    .or().like(UbmaSubscribe::getExternalOrderId, kw)
                    .or().eq(UbmaSubscribe::getSetId, safeParseIdOrNull(kw)));
        }

        // selectCount 必须在 orderByDesc + last("LIMIT..") 之前调用 (PostgreSQL 兼容).
        long total = subscribeMapper.selectCount(query);

        query.orderByDesc(UbmaSubscribe::getCreatedAt);
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 200);
        query.last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<UbmaSubscribe> rows = subscribeMapper.selectList(query);

        // 批量拉 set 名称
        Set<Long> setIds = rows.stream().map(UbmaSubscribe::getSetId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> setNameById = new HashMap<>();
        if (!setIds.isEmpty()) {
            LambdaQueryWrapper<UbmaBenefitSet> setQuery = new LambdaQueryWrapper<>();
            setQuery.in(UbmaBenefitSet::getId, setIds);
            for (UbmaBenefitSet b : benefitSetMapper.selectList(setQuery)) {
                setNameById.put(b.getId(), b.getName());
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (UbmaSubscribe sub : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("subscribe_id", IdObfuscator.toOpenId(sub.getId()));
            row.put("tenant_id", IdObfuscator.toOpenId(sub.getTenantId()));
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
    public Object getSubscriptionsSubscribeIdItems(Long tenantId, String subscribeId, String itemId) {
        Long sid = safeParseId(subscribeId);
        if (sid == null) return ApiResponse.fail(400, "无效的订阅ID");

        // 确认订阅归属本租户
        UbmaSubscribe sub = subscribeMapper.selectById(sid);
        if (sub == null || !sub.getTenantId().equals(tenantId)) {
            return ApiResponse.fail(404, "订阅不存在");
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

    // ========== 扣减流水多条件分页查询 (租户) ==========

    @Override
    public Object getConsumes(Long tenantId,
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
        // 先按 userid 过滤, 再按 consume 表的过滤组合
        Set<Long> subscribeItemIdScope = null;
        if (userid != null && !userid.isBlank()) {
            LambdaQueryWrapper<UbmaSubscribe> subQ = new LambdaQueryWrapper<>();
            subQ.eq(UbmaSubscribe::getTenantId, tenantId).eq(UbmaSubscribe::getUserid, userid.trim());
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
        query.eq(UbmaConsume::getTenantId, tenantId);
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
            String kw = keyword.trim();
            query.and(w -> w.like(UbmaConsume::getExternalOrderId, kw));
        }

        // selectCount 必须在 orderByDesc + last("LIMIT..") 之前调用 (PostgreSQL 兼容).
        long total = consumeMapper.selectCount(query);

        query.orderByDesc(UbmaConsume::getConsumeTime);
        int p = pageOr1(page);
        int s = sizeOr20(size);
        query.last("LIMIT " + s + " OFFSET " + ((p - 1) * s));
        List<UbmaConsume> rows = consumeMapper.selectList(query);

        // 批量拉 item 名称
        Set<Long> itemIds = rows.stream().map(UbmaConsume::getItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> itemNameById = new HashMap<>();
        if (!itemIds.isEmpty()) {
            LambdaQueryWrapper<UbmaBenefitItem> itemQ = new LambdaQueryWrapper<>();
            itemQ.in(UbmaBenefitItem::getId, itemIds);
            for (UbmaBenefitItem i : benefitItemMapper.selectList(itemQ)) {
                itemNameById.put(i.getId(), i.getName());
            }
        }
        // 拉 userid (按 subs_item_id -> subscribe.userid)
        Set<Long> subsItemIds = rows.stream().map(UbmaConsume::getSubsItemId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> useridBySubsItemId = new HashMap<>();
        if (!subsItemIds.isEmpty()) {
            LambdaQueryWrapper<UbmaSubscribeItem> subItemQ = new LambdaQueryWrapper<>();
            subItemQ.in(UbmaSubscribeItem::getId, subsItemIds);
            List<UbmaSubscribeItem> siList = subscribeItemMapper.selectList(subItemQ);
            Set<Long> subIds = siList.stream().map(UbmaSubscribeItem::getSubscribeId).collect(Collectors.toSet());
            Map<Long, String> useridBySubId = new HashMap<>();
            if (!subIds.isEmpty()) {
                LambdaQueryWrapper<UbmaSubscribe> subByIdQ = new LambdaQueryWrapper<>();
                subByIdQ.in(UbmaSubscribe::getId, subIds);
                for (UbmaSubscribe sub : subscribeMapper.selectList(subByIdQ)) {
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
            row.put("tenant_id", IdObfuscator.toOpenId(c.getTenantId()));
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

    // ========== 手动退减 (租户) ==========

    @Override
    @Transactional
@Auditable(action = "REFUND", targetType = "refund", targetIdSpel = "#consumeId")
    public Object postConsumesIdRefund(Long tenantId, String consumeId, PostConsumesIdRefundRequest req) {
        return doRefundConsume(tenantId, consumeId, req);
    }

    // ========== 私有: 退减核心逻辑 (租户/平台共用) ==========

    protected Object doRefundConsume(Long tenantId, String consumeId, PostConsumesIdRefundRequest req) {
        Long id = safeParseId(consumeId);
        if (id == null) return ApiResponse.fail(400, "无效的消费流水ID");
        UbmaConsume consume = consumeMapper.selectById(id);
        if (consume == null) {
            return ApiResponse.fail(404, "扣减流水不存在");
        }
        if (tenantId != null && consume.getTenantId() != null && !tenantId.equals(consume.getTenantId())) {
            return ApiResponse.fail(404, "扣减流水不存在");
        }
        if (!"COMMITTED".equals(consume.getStatus())) {
            return ApiResponse.fail(400, "仅 COMMITTED 状态可退减, 当前=" + consume.getStatus());
        }
        Integer num = consume.getConsumeNum();
        if (num == null || num <= 0) {
            return ApiResponse.fail(400, "扣减数量为0, 无需退减");
        }

        // 锁住 subscribe_item
        UbmaSubscribeItem item = subscribeItemMapper.selectById(consume.getSubsItemId());
        if (item == null) {
            return ApiResponse.fail(500, "订阅明细不存在, 账本数据不一致");
        }
        // 乐观锁更新: 总/期额度回退
        int newTotal = Math.max(0, safeInt(item.getTotalConsumed()) - num);
        int newPeriod = Math.max(0, safeInt(item.getPeriodConsumed()) - num);
        LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                .set(UbmaSubscribeItem::getTotalConsumed, newTotal)
                .set(UbmaSubscribeItem::getPeriodConsumed, newPeriod)
                .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
        int itemRows = subscribeItemMapper.update(null, itemWrapper);
        if (itemRows == 0) {
            return ApiResponse.fail(409, "并发冲突, 请重试");
        }

        // 同步回退主订阅的额度
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

        // 更新 consume 状态
        OffsetDateTime now = OffsetDateTime.now();
        java.util.Map<String, Object> ext = new LinkedHashMap<>();
        if (consume.getExt() instanceof java.util.Map) {
            ext.putAll((java.util.Map<String, Object>) consume.getExt());
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

    // ========== Private helpers ==========

    private Map<String, Object> buildSubscribeDetail(UbmaSubscribe subscribe, List<UbmaSubscribeItem> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribe_id", IdObfuscator.toOpenId(subscribe.getId()));
        result.put("userid", subscribe.getUserid());
        result.put("set_id", IdObfuscator.toOpenId(subscribe.getSetId()));
        result.put("quota_limit", subscribe.getQuotaLimit());
        result.put("total_consumed", subscribe.getTotalConsumed());
        result.put("period_consumed", subscribe.getPeriodConsumed());
        result.put("frozen_consumed", subscribe.getFrozenConsumed());
        result.put("date_begin", subscribe.getDateBegin());
        result.put("date_end", subscribe.getDateEnd());
        result.put("status", subscribe.getStatus());

        List<Map<String, Object>> itemList = new ArrayList<>();
        for (UbmaSubscribeItem item : items) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("subs_item_id", IdObfuscator.toOpenId(item.getId()));
            itemMap.put("item_id", IdObfuscator.toOpenId(item.getItemId()));
            itemMap.put("quota_limit", item.getQuotaLimit());
            itemMap.put("total_consumed", item.getTotalConsumed());
            itemMap.put("period_consumed", item.getPeriodConsumed());
            itemMap.put("frozen_consumed", item.getFrozenConsumed());
            itemList.add(itemMap);
        }
        result.put("items", itemList);
        return result;
    }

    private OffsetDateTime calculateDateEnd(OffsetDateTime begin, Integer duration, String unit) {
        if (duration == null || duration == 0) return begin.plusYears(100);
        return switch (unit != null ? unit : "month") {
            case "day" -> begin.plusDays(duration);
            case "week" -> begin.plusWeeks(duration);
            case "month" -> begin.plusMonths(duration);
            case "year" -> begin.plusYears(duration);
            default -> begin.plusMonths(duration);
        };
    }

    private OffsetDateTime calculateNextRefreshTime(OffsetDateTime begin, Integer cycle, String unit) {
        if (cycle == null || cycle == 0) return null;
        return switch (unit != null ? unit : "month") {
            case "hour" -> begin.plusHours(cycle);
            case "day" -> begin.plusDays(cycle);
            case "week" -> begin.plusWeeks(cycle);
            case "month" -> begin.plusMonths(cycle);
            case "year" -> begin.plusYears(cycle);
            default -> begin.plusMonths(cycle);
        };
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

    private Long safeParseIdOrNull(String id) {
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

    private Map<String, Object> buildPage(List<Map<String, Object>> list, long total, int page, int size) {
        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("list", list);
        pageData.put("total", total);
        pageData.put("page", page);
        pageData.put("size", size);
        return pageData;
    }
}
