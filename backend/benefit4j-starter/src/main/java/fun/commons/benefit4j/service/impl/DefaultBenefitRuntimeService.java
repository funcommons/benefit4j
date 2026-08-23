package fun.commons.benefit4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.commons.benefit4j.dto.*;
import fun.commons.benefit4j.entity.*;
import fun.commons.benefit4j.mapper.*;
import fun.commons.benefit4j.properties.Benefit4jProperties;
import fun.commons.benefit4j.service.BenefitRuntimeService;
import fun.commons.framework4j.audit.annotation.Auditable;
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
public class DefaultBenefitRuntimeService implements BenefitRuntimeService {

    private final UbmaSubscribeMapper subscribeMapper;
    private final UbmaSubscribeItemMapper subscribeItemMapper;
    private final UbmaBenefitSetMapper benefitSetMapper;
    private final UbmaBenefitRefMapper benefitRefMapper;
    private final UbmaConsumeMapper consumeMapper;
    private final UbmaApplicationMapper applicationMapper;
    private final UbmaRefundMapper refundMapper;
    private final UbmaUnsubscribeMapper unsubscribeMapper;
    private final UbmaOutboxMapper outboxMapper;
    private final Benefit4jProperties properties;

    @Override
    @Transactional
    @Auditable(action = "SUBSCRIPTION_CREATE", targetType = "subscribe",
            targetIdSpel = "#req.externalOrderId")
    public Object postSubscriptions(Long appId, PostSubscriptionsRequest req) {
        // 1. 幂等检查
        LambdaQueryWrapper<UbmaSubscribe> idempotentQuery = new LambdaQueryWrapper<>();
        idempotentQuery.eq(UbmaSubscribe::getAppId, appId)
                .eq(UbmaSubscribe::getExternalOrderId, req.getExternalOrderId());
        UbmaSubscribe existing = subscribeMapper.selectOne(idempotentQuery);
        if (existing != null) {
            return ApiResponse.success(buildSubscribeResponse(existing));
        }

        // 2. 查询权益集
        Long setId = safeParseId(req.getSetId());
        if (setId == null) return ApiResponse.fail(400, "无效的权益集ID");
        UbmaBenefitSet set = benefitSetMapper.selectById(setId);
        if (set == null || !set.getAppId().equals(appId)) {
            return ApiResponse.fail(404, "权益集不存在");
        }
        if (!"ACTIVE".equals(set.getStatus())) {
            return ApiResponse.fail(400, "权益集已停用");
        }

        // 3. 查询权益集包含的条目
        LambdaQueryWrapper<UbmaBenefitRef> refQuery = new LambdaQueryWrapper<>();
        refQuery.eq(UbmaBenefitRef::getAppId, appId)
                .eq(UbmaBenefitRef::getSetId, set.getId());
        List<UbmaBenefitRef> refs = benefitRefMapper.selectList(refQuery);
        if (refs.isEmpty()) {
            return ApiResponse.fail(400, "权益集没有配置任何权益项");
        }

        // 4. 计算有效期
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime dateBegin = now;
        OffsetDateTime dateEnd = calculateDateEnd(now, set.getDuration(), set.getDurationUnit());
        OffsetDateTime nextRefreshTime = calculateNextRefreshTime(now, set.getRefreshCycle(), set.getRefreshCycleUnit());

        // 5. 创建主订阅记录
        UbmaSubscribe subscribe = new UbmaSubscribe();
        subscribe.setAppId(appId);
        subscribe.setUserid(req.getUserid());
        subscribe.setSetId(set.getId());
        subscribe.setExternalOrderId(req.getExternalOrderId());
        subscribe.setTotalConsumed(0);
        subscribe.setPeriodConsumed(0);
        subscribe.setFrozenConsumed(0);
        subscribe.setQuotaLimit(set.getQuota());
        subscribe.setNextRefreshTime(nextRefreshTime);
        subscribe.setDateBegin(dateBegin);
        subscribe.setDateEnd(dateEnd);
        subscribe.setStatus("ACTIVE");
        subscribe.setExt(req.getExt());
        subscribe.setCreatedAt(now);
        subscribe.setUpdatedAt(now);
        subscribeMapper.insert(subscribe);

        // 6. 创建条目级订阅明细 (V1.2.0: 桶字段从 set 拷贝 sourceType / bucketPriority / expiresAt)
        for (UbmaBenefitRef ref : refs) {
            UbmaSubscribeItem item = new UbmaSubscribeItem();
            item.setAppId(appId);
            item.setSubscribeId(subscribe.getId());
            item.setItemId(ref.getItemId());
            item.setTotalConsumed(0);
            item.setPeriodConsumed(0);
            item.setFrozenConsumed(0);
            item.setQuotaLimit(ref.getQuota());
            item.setNextRefreshTime(calculateNextRefreshTime(now, ref.getRefreshCycle(), ref.getRefreshCycleUnit()));
            item.setSourceType("SUBSCRIPTION");                       // 默认值, 后续按需扩展
            item.setBucketPriority(safeInt(set.getPriority()));        // 继承 set 的优先级, 数字越大越优先扣减
            item.setExpiresAt(dateEnd);                                // 跟随 subscribe 整体过期 (per-bucket 可独立覆盖)
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            subscribeItemMapper.insert(item);
        }

        return ApiResponse.success(buildSubscribeResponse(subscribe));
    }

    @Override
    @Transactional
    public Object postSubscriptionsCancel(Long appId, PostSubscriptionsCancelRequest req) {
        Long subId = safeParseId(req.getSubscribeId());
        if (subId == null) return ApiResponse.fail(400, "无效的订阅ID");
        UbmaSubscribe subscribe = subscribeMapper.selectById(subId);
        if (subscribe == null || !subscribe.getAppId().equals(appId)) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }
        if (!"ACTIVE".equals(subscribe.getStatus())) {
            return ApiResponse.fail(400, "订阅状态不是ACTIVE，无法取消");
        }
        if (subscribe.getFrozenConsumed() != null && subscribe.getFrozenConsumed() > 0) {
            return ApiResponse.fail(400, "订阅存在冻结额度，请先释放后再取消");
        }

        // 乐观锁更新状态
        subscribe = reReadForVersion(subscribe);
        if (subscribe == null) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }
        LambdaUpdateWrapper<UbmaSubscribe> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaSubscribe::getId, subscribe.getId())
                .eq(UbmaSubscribe::getVersion, subscribe.getVersion())
                .set(UbmaSubscribe::getStatus, "CANCELED")
                .set(UbmaSubscribe::getVersion, subscribe.getVersion() + 1)
                .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
        int rows = subscribeMapper.update(null, wrapper);
        if (rows == 0) {
            return ApiResponse.fail(409, "并发冲突，请重试");
        }

        // 创建退订快照记录
        UbmaUnsubscribe unsubscribe = new UbmaUnsubscribe();
        unsubscribe.setAppId(appId);
        unsubscribe.setSubscribeId(subscribe.getId());
        unsubscribe.setExternalOrderId(req.getExternalOrderId());
        unsubscribe.setReason(req.getReason());
        unsubscribe.setCreatedAt(OffsetDateTime.now());
        unsubscribe.setUpdatedAt(OffsetDateTime.now());
        unsubscribeMapper.insert(unsubscribe);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribe_id", IdObfuscator.toOpenId(subscribe.getId()));
        result.put("status", "CANCELED");
        return ApiResponse.success(result);
    }

    @Override
    public Object getSubscriptionsSubscribeId(Long appId, String subscribeId) {
        Long subId = safeParseId(subscribeId);
        if (subId == null) return ApiResponse.fail(400, "无效的订阅ID");
        UbmaSubscribe subscribe = subscribeMapper.selectById(subId);
        if (subscribe == null || !subscribe.getAppId().equals(appId)) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }

        LambdaQueryWrapper<UbmaSubscribeItem> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.eq(UbmaSubscribeItem::getSubscribeId, subscribe.getId());
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(itemQuery);

        Map<String, Object> result = buildSubscribeDetail(subscribe, items);
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    @Auditable(action = "CONSUME_DIRECT", targetType = "consume",
            targetIdSpel = "#req.externalOrderId")
    public Object postConsumesDirect(Long appId, PostConsumesDirectRequest req) {
        int consumeNum = req.getConsumeNum() != null ? req.getConsumeNum() : 1;
        if (consumeNum <= 0) return ApiResponse.fail(400, "消费数量必须大于0");

        // 1. 幂等检查
        LambdaQueryWrapper<UbmaConsume> idempotentQuery = new LambdaQueryWrapper<>();
        idempotentQuery.eq(UbmaConsume::getAppId, appId)
                .eq(UbmaConsume::getExternalOrderId, req.getExternalOrderId());
        List<UbmaConsume> existingConsumes = consumeMapper.selectList(idempotentQuery);
        if (!existingConsumes.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("consume_id", IdObfuscator.toOpenId(existingConsumes.get(0).getId()));
            result.put("consume_ids", existingConsumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
            result.put("status", existingConsumes.get(0).getStatus());
            return ApiResponse.success(result);
        }

        // 2. 查询用户所有包含该item的ACTIVE订阅
        Long itemId = safeParseId(req.getItemId());
        if (itemId == null) return ApiResponse.fail(400, "无效的权益项ID");
        List<UbmaSubscribeItem> candidates = findDeductionCandidates(appId, req.getUserid(), itemId);
        if (candidates.isEmpty()) {
            return ApiResponse.fail(400, "用户没有可用的该权益项");
        }

        // 2.5 总额不足预检 (严格模式: partialAllowed=false 时, 总额 < 请求则整笔拒绝)
        boolean partialAllowed = req.getPartialAllowed() != null && req.getPartialAllowed();
        if (!partialAllowed) {
            int totalAvailable = 0;
            for (UbmaSubscribeItem ci : candidates) {
                totalAvailable += safeInt(ci.getQuotaLimit())
                        - safeInt(ci.getPeriodConsumed())
                        - safeInt(ci.getFrozenConsumed());
            }
            if (totalAvailable < consumeNum) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("total_available", totalAvailable);
                data.put("requested", consumeNum);
                return ApiResponse.fail(400, "INSUFFICIENT_BALANCE", data);
            }
        }

        // 3. 按优先级扣减，每个订阅项创建独立的消费流水
        int remaining = consumeNum;
        Map<Long, Integer> subDeductMap = new LinkedHashMap<>(); // subscribeId → deducted amount
        List<Long> consumeIds = new ArrayList<>();
        Long firstSubsItemId = null;
        Long firstSubscribeId = null;
        int consumeSeq = 0;

        for (UbmaSubscribeItem item : candidates) {
            int available = safeInt(item.getQuotaLimit()) - safeInt(item.getPeriodConsumed()) - safeInt(item.getFrozenConsumed());
            if (available <= 0) continue;

            int toDeduct = Math.min(remaining, available);
            // 乐观锁更新
            LambdaUpdateWrapper<UbmaSubscribeItem> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                    .set(UbmaSubscribeItem::getPeriodConsumed, safeInt(item.getPeriodConsumed()) + toDeduct)
                    .set(UbmaSubscribeItem::getTotalConsumed, safeInt(item.getTotalConsumed()) + toDeduct)
                    .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                    .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
            int rows = subscribeItemMapper.update(null, wrapper);
            if (rows == 0) {
                // 并发冲突, 重读 version 重试 2 次 (防止高并发跳桶→扣减不足)
                for (int r = 0; r < 2 && rows == 0; r++) {
                    UbmaSubscribeItem fresh = subscribeItemMapper.selectById(item.getId());
                    if (fresh == null) break;
                    int freshAvailable = safeInt(fresh.getQuotaLimit()) - safeInt(fresh.getPeriodConsumed()) - safeInt(fresh.getFrozenConsumed());
                    if (freshAvailable <= 0) break;
                    toDeduct = Math.min(remaining, freshAvailable);
                    LambdaUpdateWrapper<UbmaSubscribeItem> retryWrapper = new LambdaUpdateWrapper<>();
                    retryWrapper.eq(UbmaSubscribeItem::getId, fresh.getId())
                            .eq(UbmaSubscribeItem::getVersion, fresh.getVersion())
                            .set(UbmaSubscribeItem::getPeriodConsumed, safeInt(fresh.getPeriodConsumed()) + toDeduct)
                            .set(UbmaSubscribeItem::getTotalConsumed, safeInt(fresh.getTotalConsumed()) + toDeduct)
                            .set(UbmaSubscribeItem::getVersion, fresh.getVersion() + 1)
                            .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
                    rows = subscribeItemMapper.update(null, retryWrapper);
                }
                if (rows == 0) continue;
            }

            if (firstSubsItemId == null) {
                firstSubsItemId = item.getId();
                firstSubscribeId = item.getSubscribeId();
            }
            subDeductMap.merge(item.getSubscribeId(), toDeduct, Integer::sum);

            // 为每个订阅项创建独立的消费流水
            UbmaConsume consume = new UbmaConsume();
            consume.setAppId(appId);
            consume.setSubsItemId(item.getId());
            consume.setItemId(itemId);
            consume.setExternalOrderId(consumeSeq == 0 ? req.getExternalOrderId() : req.getExternalOrderId() + "--" + consumeSeq);
            consume.setConsumeNum(toDeduct);
            consume.setStatus("COMMITTED");
            consume.setConsumeTime(OffsetDateTime.now());
            consume.setCreatedAt(OffsetDateTime.now());
            consume.setUpdatedAt(OffsetDateTime.now());
            consumeMapper.insert(consume);
            // outbox 事件 (同事务, 分布式事务预留: 拆服务时改发 MQ)
            Map<String, Object> consumePayload = new HashMap<>();
            consumePayload.put("consumeId", consume.getId());
            consumePayload.put("externalOrderId", consume.getExternalOrderId());
            consumePayload.put("consumeNum", consume.getConsumeNum());
            consumePayload.put("itemId", consume.getItemId());
            writeOutbox("consume", consume.getId(), "COMMITTED", consumePayload);
            consumeIds.add(consume.getId());
            consumeSeq++;

            remaining -= toDeduct;
            if (remaining <= 0) break;
        }

        // partialAllowed=false 且部分扣减不足 → throw 触发事务回滚 (已扣桶回滚)
        if (remaining > 0 && !partialAllowed) {
            throw new RuntimeException("INSUFFICIENT_BALANCE: 部分扣减不允许, 已扣 "
                    + (consumeNum - remaining) + "/" + consumeNum + ", 触发事务回滚");
        }
        if (remaining == consumeNum) {
            return ApiResponse.fail(400, "额度不足, 扣减失败");
        }

        // 4. 更新集合级消耗（支持跨订阅扣减）
        for (Map.Entry<Long, Integer> entry : subDeductMap.entrySet()) {
            UbmaSubscribe sub = reReadForVersion(subscribeMapper.selectById(entry.getKey()));
            if (sub == null) continue;
            LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
            subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, sub.getVersion())
                    .set(UbmaSubscribe::getPeriodConsumed, safeInt(sub.getPeriodConsumed()) + entry.getValue())
                    .set(UbmaSubscribe::getTotalConsumed, safeInt(sub.getTotalConsumed()) + entry.getValue())
                    .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, subWrapper);

            checkAndUpdateExhausted(sub.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consume_id", IdObfuscator.toOpenId(consumeIds.get(0)));
        result.put("consume_ids", consumeIds.stream().map(IdObfuscator::toOpenId).collect(Collectors.toList()));
        result.put("subscribe_id", IdObfuscator.toOpenId(firstSubscribeId));
        result.put("subs_item_id", IdObfuscator.toOpenId(firstSubsItemId));
        result.put("consume_num", consumeNum - remaining);
        result.put("status", "COMMITTED");
        // 附加当前额度快照（取首个扣减的订阅项）
        UbmaSubscribeItem firstItem = subscribeItemMapper.selectById(firstSubsItemId);
        if (firstItem != null) {
            result.put("current_frozen_consumed", safeInt(firstItem.getFrozenConsumed()));
            result.put("current_quota_limit", safeInt(firstItem.getQuotaLimit()));
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object postConsumesReserve(Long appId, PostConsumesReserveRequest req) {
        int consumeNum = req.getConsumeNum() != null ? req.getConsumeNum() : 1;
        if (consumeNum <= 0) return ApiResponse.fail(400, "消费数量必须大于0");

        // 1. 幂等检查
        LambdaQueryWrapper<UbmaConsume> idempotentQuery = new LambdaQueryWrapper<>();
        idempotentQuery.eq(UbmaConsume::getAppId, appId)
                .eq(UbmaConsume::getExternalOrderId, req.getExternalOrderId());
        List<UbmaConsume> existingConsumes = consumeMapper.selectList(idempotentQuery);
        if (!existingConsumes.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("consume_id", IdObfuscator.toOpenId(existingConsumes.get(0).getId()));
            result.put("consume_ids", existingConsumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
            result.put("subs_item_id", IdObfuscator.toOpenId(existingConsumes.get(0).getSubsItemId()));
            result.put("status", existingConsumes.get(0).getStatus());
            return ApiResponse.success(result);
        }

        // 2. 查询用户所有包含该item的ACTIVE订阅（按优先级排序）
        Long itemId = safeParseId(req.getItemId());
        if (itemId == null) return ApiResponse.fail(400, "无效的权益项ID");
        List<UbmaSubscribeItem> candidates = findDeductionCandidates(appId, req.getUserid(), itemId);
        if (candidates.isEmpty()) {
            return ApiResponse.fail(400, "用户没有可用的该权益项");
        }

        // 3. 冻结额度，每个订阅项创建独立的消费流水
        int remaining = consumeNum;
        Map<Long, Integer> subFreezeMap = new LinkedHashMap<>(); // subscribeId → frozen amount
        List<UbmaConsume> reservedConsumes = new ArrayList<>();
        Long firstSubsItemId = null;
        Long firstSubscribeId = null;
        int timeoutSeconds = req.getTimeoutSeconds() != null ? req.getTimeoutSeconds() : properties.getReserveTimeoutSeconds();
        OffsetDateTime expireTime = OffsetDateTime.now().plusSeconds(timeoutSeconds);

        for (UbmaSubscribeItem item : candidates) {
            int available = safeInt(item.getQuotaLimit()) - safeInt(item.getPeriodConsumed()) - safeInt(item.getFrozenConsumed());
            if (available <= 0) continue;

            int toFreeze = Math.min(remaining, available);
            LambdaUpdateWrapper<UbmaSubscribeItem> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                    .set(UbmaSubscribeItem::getFrozenConsumed, safeInt(item.getFrozenConsumed()) + toFreeze)
                    .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                    .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
            int rows = subscribeItemMapper.update(null, wrapper);
            if (rows == 0) continue;

            if (firstSubsItemId == null) {
                firstSubsItemId = item.getId();
                firstSubscribeId = item.getSubscribeId();
            }
            subFreezeMap.merge(item.getSubscribeId(), toFreeze, Integer::sum);

            // 为每个订阅项创建独立的RESERVED消费流水
            UbmaConsume consume = new UbmaConsume();
            consume.setAppId(appId);
            consume.setSubsItemId(item.getId());
            consume.setItemId(itemId);
            consume.setExternalOrderId(req.getExternalOrderId());
            consume.setConsumeNum(toFreeze);
            consume.setStatus("RESERVED");
            consume.setConsumeTime(OffsetDateTime.now());
            consume.setExpireTime(expireTime);
            consume.setCreatedAt(OffsetDateTime.now());
            consume.setUpdatedAt(OffsetDateTime.now());
            consumeMapper.insert(consume);
            reservedConsumes.add(consume);

            remaining -= toFreeze;
            if (remaining <= 0) break;
        }

        if (remaining == consumeNum) {
            return ApiResponse.fail(400, "额度不足，冻结失败");
        }

        // 4. 更新集合级冻结（支持跨订阅冻结）
        for (Map.Entry<Long, Integer> entry : subFreezeMap.entrySet()) {
            UbmaSubscribe sub = reReadForVersion(subscribeMapper.selectById(entry.getKey()));
            if (sub == null) continue;
            LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
            subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, sub.getVersion())
                    .set(UbmaSubscribe::getFrozenConsumed, safeInt(sub.getFrozenConsumed()) + entry.getValue())
                    .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, subWrapper);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consume_id", IdObfuscator.toOpenId(reservedConsumes.get(0).getId()));
        result.put("consume_ids", reservedConsumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
        result.put("subscribe_id", IdObfuscator.toOpenId(firstSubscribeId));
        result.put("subs_item_id", IdObfuscator.toOpenId(firstSubsItemId));
        result.put("consume_num", consumeNum - remaining);
        result.put("status", "RESERVED");
        // 附加当前额度快照（取首个冻结的订阅项）
        UbmaSubscribeItem firstItem = subscribeItemMapper.selectById(firstSubsItemId);
        if (firstItem != null) {
            result.put("current_frozen_consumed", safeInt(firstItem.getFrozenConsumed()));
            result.put("current_quota_limit", safeInt(firstItem.getQuotaLimit()));
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object postConsumesCommit(Long appId, PostConsumesCommitRequest req) {
        // 1. 查找所有RESERVED消费记录（跨订阅扣减会产生多条）
        LambdaQueryWrapper<UbmaConsume> query = new LambdaQueryWrapper<>();
        query.eq(UbmaConsume::getAppId, appId)
                .eq(UbmaConsume::getExternalOrderId, req.getExternalOrderId());
        List<UbmaConsume> consumes = consumeMapper.selectList(query);
        if (consumes.isEmpty()) {
            return ApiResponse.fail(404, "预留记录不存在");
        }

        // 检查是否全部已非RESERVED状态
        List<UbmaConsume> reservedConsumes = consumes.stream()
                .filter(c -> "RESERVED".equals(c.getStatus())).collect(Collectors.toList());
        if (reservedConsumes.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("consume_id", IdObfuscator.toOpenId(consumes.get(0).getId()));
            result.put("consume_ids", consumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
            result.put("status", consumes.get(0).getStatus());
            return ApiResponse.success(result);
        }

        // 检查是否已超时
        OffsetDateTime now = OffsetDateTime.now();
        boolean anyExpired = reservedConsumes.stream()
                .anyMatch(c -> c.getExpireTime() != null && now.isAfter(c.getExpireTime()));
        if (anyExpired) {
            return ApiResponse.fail(400, "预扣已超时，请重新发起扣减");
        }

        // 2. 逐条处理：冻结转已消费
        Map<Long, Integer> subCommitMap = new LinkedHashMap<>(); // subscribeId → commit amount
        for (UbmaConsume consume : reservedConsumes) {
            UbmaSubscribeItem item = subscribeItemMapper.selectById(consume.getSubsItemId());
            if (item == null || !item.getAppId().equals(appId)) continue;

            LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
            itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                    .set(UbmaSubscribeItem::getFrozenConsumed, safeInt(item.getFrozenConsumed()) - safeInt(consume.getConsumeNum()))
                    .set(UbmaSubscribeItem::getPeriodConsumed, safeInt(item.getPeriodConsumed()) + safeInt(consume.getConsumeNum()))
                    .set(UbmaSubscribeItem::getTotalConsumed, safeInt(item.getTotalConsumed()) + safeInt(consume.getConsumeNum()))
                    .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                    .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
            int itemRows = subscribeItemMapper.update(null, itemWrapper);
            if (itemRows == 0) continue;

            subCommitMap.merge(item.getSubscribeId(), safeInt(consume.getConsumeNum()), Integer::sum);

            // 更新消费流水状态
            LambdaUpdateWrapper<UbmaConsume> consumeWrapper = new LambdaUpdateWrapper<>();
            consumeWrapper.eq(UbmaConsume::getId, consume.getId())
                    .set(UbmaConsume::getStatus, "COMMITTED")
                    .set(UbmaConsume::getConsumeTime, OffsetDateTime.now())
                    .set(UbmaConsume::getUpdatedAt, OffsetDateTime.now());
            consumeMapper.update(null, consumeWrapper);
        }

        // 3. 集合级：冻结转已消费
        for (Map.Entry<Long, Integer> entry : subCommitMap.entrySet()) {
            UbmaSubscribe sub = reReadForVersion(subscribeMapper.selectById(entry.getKey()));
            if (sub == null) continue;
            LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
            subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, sub.getVersion())
                    .set(UbmaSubscribe::getFrozenConsumed, safeInt(sub.getFrozenConsumed()) - entry.getValue())
                    .set(UbmaSubscribe::getPeriodConsumed, safeInt(sub.getPeriodConsumed()) + entry.getValue())
                    .set(UbmaSubscribe::getTotalConsumed, safeInt(sub.getTotalConsumed()) + entry.getValue())
                    .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, subWrapper);

            checkAndUpdateExhausted(sub.getId());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consume_id", IdObfuscator.toOpenId(reservedConsumes.get(0).getId()));
        result.put("consume_ids", reservedConsumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
        result.put("status", "COMMITTED");
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public Object postConsumesRelease(Long appId, PostConsumesReleaseRequest req) {
        // 1. 查找所有RESERVED消费记录（跨订阅扣减会产生多条）
        LambdaQueryWrapper<UbmaConsume> query = new LambdaQueryWrapper<>();
        query.eq(UbmaConsume::getAppId, appId)
                .eq(UbmaConsume::getExternalOrderId, req.getExternalOrderId());
        List<UbmaConsume> consumes = consumeMapper.selectList(query);
        if (consumes.isEmpty()) {
            return ApiResponse.fail(404, "预留记录不存在");
        }

        List<UbmaConsume> reservedConsumes = consumes.stream()
                .filter(c -> "RESERVED".equals(c.getStatus())).collect(Collectors.toList());
        if (reservedConsumes.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("consume_id", IdObfuscator.toOpenId(consumes.get(0).getId()));
            result.put("consume_ids", consumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
            result.put("status", consumes.get(0).getStatus());
            return ApiResponse.success(result);
        }

        // 检查是否已超时
        OffsetDateTime now = OffsetDateTime.now();
        boolean anyExpired = reservedConsumes.stream()
                .anyMatch(c -> c.getExpireTime() != null && now.isAfter(c.getExpireTime()));
        if (anyExpired) {
            return ApiResponse.fail(400, "预扣已超时，额度已自动释放");
        }

        // 2. 逐条处理：解冻
        Map<Long, Integer> subReleaseMap = new LinkedHashMap<>(); // subscribeId → release amount
        for (UbmaConsume consume : reservedConsumes) {
            UbmaSubscribeItem item = subscribeItemMapper.selectById(consume.getSubsItemId());
            if (item == null || !item.getAppId().equals(appId)) continue;

            LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
            itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                    .set(UbmaSubscribeItem::getFrozenConsumed, safeInt(item.getFrozenConsumed()) - safeInt(consume.getConsumeNum()))
                    .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                    .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
            int itemRows = subscribeItemMapper.update(null, itemWrapper);
            if (itemRows == 0) continue;

            subReleaseMap.merge(item.getSubscribeId(), safeInt(consume.getConsumeNum()), Integer::sum);

            // 更新消费流水状态
            LambdaUpdateWrapper<UbmaConsume> consumeWrapper = new LambdaUpdateWrapper<>();
            consumeWrapper.eq(UbmaConsume::getId, consume.getId())
                    .set(UbmaConsume::getStatus, "RELEASED")
                    .set(UbmaConsume::getUpdatedAt, OffsetDateTime.now());
            consumeMapper.update(null, consumeWrapper);
        }

        // 3. 集合级：解冻
        for (Map.Entry<Long, Integer> entry : subReleaseMap.entrySet()) {
            UbmaSubscribe sub = reReadForVersion(subscribeMapper.selectById(entry.getKey()));
            if (sub == null) continue;
            LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
            subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, sub.getVersion())
                    .set(UbmaSubscribe::getFrozenConsumed, safeInt(sub.getFrozenConsumed()) - entry.getValue())
                    .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, subWrapper);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("consume_id", IdObfuscator.toOpenId(reservedConsumes.get(0).getId()));
        result.put("consume_ids", reservedConsumes.stream().map(c -> IdObfuscator.toOpenId(c.getId())).collect(Collectors.toList()));
        result.put("status", "RELEASED");
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    @Auditable(action = "REFUND", targetType = "refund", targetIdSpel = "#req.externalRefundId")
    public Object postRefunds(Long appId, PostRefundsRequest req) {
        // 1. 查找消费记录
        Long consumeId = safeParseId(req.getConsumeId());
        if (consumeId == null) return ApiResponse.fail(400, "无效的消费ID");
        UbmaConsume consume = consumeMapper.selectById(consumeId);
        if (consume == null || !consume.getAppId().equals(appId)) {
            return ApiResponse.fail(404, "消费记录不存在");
        }
        if (!"COMMITTED".equals(consume.getStatus())) {
            return ApiResponse.fail(400, "消费记录状态不是COMMITTED，无法退款");
        }

        // 2. 幂等检查
        LambdaQueryWrapper<UbmaRefund> idempotentQuery = new LambdaQueryWrapper<>();
        idempotentQuery.eq(UbmaRefund::getAppId, appId)
                .eq(UbmaRefund::getExternalRefundId, req.getExternalRefundId());
        UbmaRefund existingRefund = refundMapper.selectOne(idempotentQuery);
        if (existingRefund != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("refund_id", IdObfuscator.toOpenId(existingRefund.getId()));
            result.put("refund_num", existingRefund.getRefundNum());
            return ApiResponse.success(result);
        }

        int refundNum = req.getRefundNum() != null ? req.getRefundNum() : consume.getConsumeNum();
        if (refundNum <= 0) return ApiResponse.fail(400, "退款数量必须大于0");
        if (refundNum > consume.getConsumeNum()) {
            return ApiResponse.fail(400, "退款数量不能超过消费数量");
        }

        // 累计退款检查
        LambdaQueryWrapper<UbmaRefund> totalRefundQuery = new LambdaQueryWrapper<>();
        totalRefundQuery.eq(UbmaRefund::getConsumeId, consume.getId());
        List<UbmaRefund> prevRefunds = refundMapper.selectList(totalRefundQuery);
        int totalRefunded = prevRefunds.stream().mapToInt(UbmaRefund::getRefundNum).sum();
        if (totalRefunded + refundNum > consume.getConsumeNum()) {
            return ApiResponse.fail(400, "累计退款数量不能超过消费数量");
        }

        // 3. 条目级：回退已消费
        UbmaSubscribeItem item = subscribeItemMapper.selectById(consume.getSubsItemId());
        if (item == null || !item.getAppId().equals(appId)) {
            return ApiResponse.fail(404, "订阅明细不存在");
        }
        LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
        itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                .set(UbmaSubscribeItem::getPeriodConsumed, safeInt(item.getPeriodConsumed()) - refundNum)
                .set(UbmaSubscribeItem::getTotalConsumed, safeInt(item.getTotalConsumed()) - refundNum)
                .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
        int itemRows = subscribeItemMapper.update(null, itemWrapper);
        if (itemRows == 0) {
            return ApiResponse.fail(409, "并发冲突，请重试");
        }

        // 4. 集合级：回退已消费
        UbmaSubscribe sub = subscribeMapper.selectById(item.getSubscribeId());
        if (sub == null) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }
        sub = reReadForVersion(sub);
        if (sub == null) {
            return ApiResponse.fail(404, "订阅记录不存在");
        }
        LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
        subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                .eq(UbmaSubscribe::getVersion, sub.getVersion())
                .set(UbmaSubscribe::getPeriodConsumed, safeInt(sub.getPeriodConsumed()) - refundNum)
                .set(UbmaSubscribe::getTotalConsumed, safeInt(sub.getTotalConsumed()) - refundNum)
                .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
        subscribeMapper.update(null, subWrapper);

        // 退款后检查是否需要从EXHAUSTED恢复为ACTIVE
        if ("EXHAUSTED".equals(sub.getStatus())) {
            int newPeriodConsumed = safeInt(sub.getPeriodConsumed()) - refundNum;
            int newFrozenConsumed = safeInt(sub.getFrozenConsumed());
            int quotaLimit = safeInt(sub.getQuotaLimit());
            if (quotaLimit > 0 && newPeriodConsumed + newFrozenConsumed < quotaLimit) {
                UbmaSubscribe freshSub = reReadForVersion(subscribeMapper.selectById(sub.getId()));
                if (freshSub != null) {
                    LambdaUpdateWrapper<UbmaSubscribe> reactivateWrapper = new LambdaUpdateWrapper<>();
                    reactivateWrapper.eq(UbmaSubscribe::getId, freshSub.getId())
                            .eq(UbmaSubscribe::getVersion, freshSub.getVersion())
                            .set(UbmaSubscribe::getStatus, "ACTIVE")
                            .set(UbmaSubscribe::getVersion, freshSub.getVersion() + 1)
                            .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
                    subscribeMapper.update(null, reactivateWrapper);
                }
            }
        }

        // 5. 创建退款记录
        UbmaRefund refund = new UbmaRefund();
        refund.setAppId(appId);
        refund.setConsumeId(consume.getId());
        refund.setExternalRefundId(req.getExternalRefundId());
        refund.setRefundNum(refundNum);
        refund.setRefundTime(OffsetDateTime.now());
        refund.setCreatedAt(OffsetDateTime.now());
        refund.setUpdatedAt(OffsetDateTime.now());
        refundMapper.insert(refund);
        // outbox 事件 (退款, 分布式事务预留)
        Map<String, Object> refundEvent = new LinkedHashMap<>();
        refundEvent.put("refundId", refund.getId());
        refundEvent.put("consumeId", refund.getConsumeId());
        refundEvent.put("externalRefundId", refund.getExternalRefundId());
        refundEvent.put("refundNum", refund.getRefundNum());
        writeOutbox("refund", refund.getId(), "REFUNDED", refundEvent);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refund_id", IdObfuscator.toOpenId(refund.getId()));
        result.put("consume_id", IdObfuscator.toOpenId(consume.getId()));
        result.put("refund_num", refundNum);
        return ApiResponse.success(result);
    }

    @Override
    public Object getUsersUseridAssets(Long appId, String userid) {
        LambdaQueryWrapper<UbmaSubscribe> query = new LambdaQueryWrapper<>();
        query.eq(UbmaSubscribe::getAppId, appId)
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
    public Object getUsersUseridConsumes(Long appId, String userid) {
        // 查找用户的所有订阅ID
        LambdaQueryWrapper<UbmaSubscribe> subQuery = new LambdaQueryWrapper<>();
        subQuery.eq(UbmaSubscribe::getAppId, appId)
                .eq(UbmaSubscribe::getUserid, userid);
        List<UbmaSubscribe> subscribes = subscribeMapper.selectList(subQuery);

        if (subscribes.isEmpty()) {
            return ApiResponse.success(new Page<>());
        }

        Set<Long> subscribeIds = subscribes.stream().map(UbmaSubscribe::getId).collect(Collectors.toSet());

        // 查找所有订阅明细的ID
        LambdaQueryWrapper<UbmaSubscribeItem> itemQuery = new LambdaQueryWrapper<>();
        itemQuery.in(UbmaSubscribeItem::getSubscribeId, subscribeIds);
        List<UbmaSubscribeItem> items = subscribeItemMapper.selectList(itemQuery);

        if (items.isEmpty()) {
            return ApiResponse.success(new Page<>());
        }

        Set<Long> subsItemIds = items.stream().map(UbmaSubscribeItem::getId).collect(Collectors.toSet());

        // 查找消费流水
        LambdaQueryWrapper<UbmaConsume> consumeQuery = new LambdaQueryWrapper<>();
        consumeQuery.in(UbmaConsume::getSubsItemId, subsItemIds)
                .orderByDesc(UbmaConsume::getConsumeTime);
        List<UbmaConsume> consumes = consumeMapper.selectList(consumeQuery);

        List<Map<String, Object>> result = new ArrayList<>();
        for (UbmaConsume c : consumes) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("consume_id", IdObfuscator.toOpenId(c.getId()));
            row.put("app_id", IdObfuscator.toOpenId(c.getAppId()));
            row.put("subs_item_id", IdObfuscator.toOpenId(c.getSubsItemId()));
            row.put("item_id", IdObfuscator.toOpenId(c.getItemId()));
            row.put("external_order_id", c.getExternalOrderId());
            row.put("consume_num", c.getConsumeNum());
            row.put("status", c.getStatus());
            row.put("consume_time", c.getConsumeTime());
            row.put("expire_time", c.getExpireTime());
            result.add(row);
        }
        return ApiResponse.success(result);
    }

    @Override
    @Transactional
    public int releaseExpiredReserves() {
        LambdaQueryWrapper<UbmaConsume> query = new LambdaQueryWrapper<>();
        query.eq(UbmaConsume::getStatus, "RESERVED")
                .le(UbmaConsume::getExpireTime, OffsetDateTime.now());
        List<UbmaConsume> expiredConsumes = consumeMapper.selectList(query);
        if (expiredConsumes.isEmpty()) return 0;

        Map<Long, Integer> subReleaseMap = new LinkedHashMap<>();
        for (UbmaConsume consume : expiredConsumes) {
            UbmaSubscribeItem item = subscribeItemMapper.selectById(consume.getSubsItemId());
            if (item == null) continue;

            LambdaUpdateWrapper<UbmaSubscribeItem> itemWrapper = new LambdaUpdateWrapper<>();
            itemWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, item.getVersion())
                    .set(UbmaSubscribeItem::getFrozenConsumed, safeInt(item.getFrozenConsumed()) - safeInt(consume.getConsumeNum()))
                    .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1)
                    .set(UbmaSubscribeItem::getUpdatedAt, OffsetDateTime.now());
            int rows = subscribeItemMapper.update(null, itemWrapper);
            if (rows == 0) continue;

            subReleaseMap.merge(item.getSubscribeId(), safeInt(consume.getConsumeNum()), Integer::sum);

            LambdaUpdateWrapper<UbmaConsume> consumeWrapper = new LambdaUpdateWrapper<>();
            consumeWrapper.eq(UbmaConsume::getId, consume.getId())
                    .set(UbmaConsume::getStatus, "EXPIRED")
                    .set(UbmaConsume::getUpdatedAt, OffsetDateTime.now());
            consumeMapper.update(null, consumeWrapper);
        }

        for (Map.Entry<Long, Integer> entry : subReleaseMap.entrySet()) {
            UbmaSubscribe sub = reReadForVersion(subscribeMapper.selectById(entry.getKey()));
            if (sub == null) continue;
            LambdaUpdateWrapper<UbmaSubscribe> subWrapper = new LambdaUpdateWrapper<>();
            subWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, sub.getVersion())
                    .set(UbmaSubscribe::getFrozenConsumed, safeInt(sub.getFrozenConsumed()) - entry.getValue())
                    .set(UbmaSubscribe::getVersion, sub.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, subWrapper);
        }

        return expiredConsumes.size();
    }

    // ========== Private helpers ==========

    private List<UbmaSubscribeItem> findDeductionCandidates(Long appId, String userid, Long itemId) {
        // 单次查询拉所有候选桶
        // 1) 通过 EXISTS 子查询过滤出 ACTIVE 订阅对应的桶 (半连接短路, 比 IN 更易被 PG 优化)
        // 2) 过滤未过期桶 (NULL 或 expires_at > now)
        // 3) 排序交给数据库: bucket_priority DESC -> expires_at ASC NULLS LAST -> created_at ASC
        //    数据库层走 idx_user_item_active_priority 索引, 无 in-memory Sort
        //    数字越大越优先扣减 (沿用 ubma_benefit_set.priority 注释约定)
        OffsetDateTime now = OffsetDateTime.now();
        LambdaQueryWrapper<UbmaSubscribeItem> q = new LambdaQueryWrapper<>();
        q.eq(UbmaSubscribeItem::getAppId, appId)
                .eq(UbmaSubscribeItem::getItemId, itemId)
                .apply("EXISTS (SELECT 1 FROM ubma_subscribe s WHERE s.id = ubma_subscribe_item.subscribe_id AND s.app_id = {0} AND s.userid = {1} AND s.status = 'ACTIVE' AND s.is_deleted = 0)",
                        appId, userid)
                .and(w -> w.isNull(UbmaSubscribeItem::getExpiresAt)
                        .or().gt(UbmaSubscribeItem::getExpiresAt, now))
                .orderByDesc(UbmaSubscribeItem::getBucketPriority)
                .orderByAsc(UbmaSubscribeItem::getExpiresAt)
                .orderByAsc(UbmaSubscribeItem::getCreatedAt);
        List<UbmaSubscribeItem> candidates = subscribeItemMapper.selectList(q);
        // 兜底: 应用层 NULLS LAST 修正 + 优先级一致性 (DB 默认 NULLS FIRST for ASC, 桶优先级为 NULL 时不参与排序即可)
        // 包成 mutable list: 测试桩可能返回 List.of()/Collections.emptyList() 这类不可变列表, 直接 sort 会 UOE
        List<UbmaSubscribeItem> mutable = new ArrayList<>(candidates);
        mutable.sort(Comparator
                .comparingInt((UbmaSubscribeItem b) -> -safeInt(b.getBucketPriority()))
                .thenComparing(UbmaSubscribeItem::getExpiresAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(UbmaSubscribeItem::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return mutable;
    }

    private void checkAndUpdateExhausted(Long subscribeId) {
        UbmaSubscribe subscribe = subscribeMapper.selectById(subscribeId);
        if (subscribe == null || !"ACTIVE".equals(subscribe.getStatus())) return;
        if (safeInt(subscribe.getQuotaLimit()) <= 0) return; // 不限额

        if (safeInt(subscribe.getPeriodConsumed()) + safeInt(subscribe.getFrozenConsumed()) >= safeInt(subscribe.getQuotaLimit())) {
            subscribe = reReadForVersion(subscribe);
            if (subscribe == null) return;
            LambdaUpdateWrapper<UbmaSubscribe> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(UbmaSubscribe::getId, subscribe.getId())
                    .eq(UbmaSubscribe::getVersion, subscribe.getVersion())
                    .set(UbmaSubscribe::getStatus, "EXHAUSTED")
                    .set(UbmaSubscribe::getVersion, subscribe.getVersion() + 1)
                    .set(UbmaSubscribe::getUpdatedAt, OffsetDateTime.now());
            subscribeMapper.update(null, wrapper);
        }
    }

    private UbmaSubscribe reReadForVersion(UbmaSubscribe sub) {
        return subscribeMapper.selectById(sub.getId());
    }

    private OffsetDateTime calculateDateEnd(OffsetDateTime begin, Integer duration, String unit) {
        if (duration == null || duration == 0) {
            return begin.plusYears(100); // 永久
        }
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

    private Map<String, Object> buildSubscribeResponse(UbmaSubscribe subscribe) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("subscribe_id", IdObfuscator.toOpenId(subscribe.getId()));
        result.put("date_begin", subscribe.getDateBegin());
        result.put("date_end", subscribe.getDateEnd());
        result.put("status", subscribe.getStatus());
        return result;
    }

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

    /**
     * 写 outbox 事件 (同事务, 原子)。
     * 单体内无 MQ 消费者, OutboxPublisher 标记 SENT; 拆服务时改发 Kafka。
     */
    private void writeOutbox(String aggregateType, Long aggregateId, String eventType, Object payload) {
        UbmaOutbox outbox = new UbmaOutbox();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payload);
        outbox.setStatus("PENDING");
        OffsetDateTime now = OffsetDateTime.now();
        outbox.setCreatedAt(now);
        outboxMapper.insert(outbox);
    }
}
