package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxPostingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * limit_policy 反洗钱限额(assets-design §2.1 O3 / §4.3.1 规则 2):
 *   singleMax 单笔 / dailyMax 日累计 / monthlyMax 月累计;
 *   口径 = Asia/Shanghai 营业日(不取服务器时区);维度 = 账户。
 *   入账侧 checkIn(dst 累计)/ 出账侧 checkOut(src 累计),无 limit_policy 不拦。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssetsLimitGuard {

    static final ZoneId BIZ_ZONE = ZoneId.of("Asia/Shanghai");

    private final AssetRegistryService registry;
    private final UbmxPostingMapper postingMapper;

    /** 引擎腿级校验: src NORMAL → 出账限额,dst NORMAL → 入账限额 */
    public void checkLeg(UbmxAccount src, UbmxAccount dst, String assetCode, BigDecimal amount) {
        if (!"BOUNDARY".equals(src.getAccountType())) {
            checkOut(src, assetCode, amount);
        }
        if (!"BOUNDARY".equals(dst.getAccountType())) {
            checkIn(dst, assetCode, amount);
        }
    }

    public void checkIn(UbmxAccount account, String assetCode, BigDecimal amount) {
        check(account, assetCode, amount, true);
    }

    public void checkOut(UbmxAccount account, String assetCode, BigDecimal amount) {
        check(account, assetCode, amount, false);
    }

    private void check(UbmxAccount account, String assetCode, BigDecimal amount, boolean inbound) {
        Map<String, Object> policy = dirPolicyOf(assetCode, inbound);
        if (policy == null || policy.isEmpty()) {
            return;   // 未配限额不拦(VIRTUAL 推荐、FIAT 强制由注册侧把关)
        }
        BigDecimal single = toDecimal(policy.get("singleMax"));
        BigDecimal daily = toDecimal(policy.get("dailyMax"));
        BigDecimal monthly = toDecimal(policy.get("monthlyMax"));

        if (single != null && amount.compareTo(single) > 0) {
            reject(account, assetCode, "单笔超限", amount, single);
        }
        if (daily != null || monthly != null) {
            OffsetDateTime now = OffsetDateTime.now(BIZ_ZONE);
            if (daily != null) {
                BigDecimal daySum = sumSince(account, inbound, dayStart(now));
                if (daySum.add(amount).compareTo(daily) > 0) {
                    reject(account, assetCode, "日累计超限(Asia/Shanghai)", daySum.add(amount), daily);
                }
            }
            if (monthly != null) {
                BigDecimal monthSum = sumSince(account, inbound, monthStart(now));
                if (monthSum.add(amount).compareTo(monthly) > 0) {
                    reject(account, assetCode, "月累计超限", monthSum.add(amount), monthly);
                }
            }
        }
    }

    private void reject(UbmxAccount account, String assetCode, String reason,
                        BigDecimal actual, BigDecimal max) {
        throw new AssetsException(AssetsException.LIMIT_EXCEEDED,
                reason + ": asset=" + assetCode + " accountId=" + account.getId()
                        + " amount=" + actual + " max=" + max);
    }

    /**
     * 方向策略: {"in":{...},"out":{...}} 显式分组;
     * 扁平键(设计示例形态)= 入账侧(AML 充值限额);出账侧未配 out 不拦。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> dirPolicyOf(String assetCode, boolean inbound) {
        Map<String, Object> full = policyOf(assetCode);
        if (full == null) return null;
        Object dir = full.get(inbound ? "in" : "out");
        if (dir instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return inbound ? full : Map.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> policyOf(String assetCode) {
        UbmxAsset asset = registry.getActiveRequired(assetCode);
        Object policy = asset.getLimitPolicy();
        return policy instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private BigDecimal toDecimal(Object value) {
        if (value == null) return null;
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            log.warn("[assets] limit_policy 数值非法: {}", value);
            return null;
        }
    }

    private BigDecimal sumSince(UbmxAccount account, boolean inbound, OffsetDateTime since) {
        BigDecimal sum = inbound
                ? postingMapper.sumInSince(account.getAppId(), account.getId(), since)
                : postingMapper.sumOutSince(account.getAppId(), account.getId(), since);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    /** Asia/Shanghai 当日 00:00(timestamptz 比较)。必须先转到营业时区再取日期,
     *  否则 UTC 傍晚传入会按 UTC 日期算成前一天(单测 AssetsPureLogicTest 抓出)。 */
    static OffsetDateTime dayStart(OffsetDateTime now) {
        LocalDate d = now.atZoneSameInstant(BIZ_ZONE).toLocalDate();
        return d.atStartOfDay(BIZ_ZONE).toOffsetDateTime();
    }

    /** Asia/Shanghai 当月 1 日 00:00(同样先转营业时区) */
    static OffsetDateTime monthStart(OffsetDateTime now) {
        LocalDate d = now.atZoneSameInstant(BIZ_ZONE).toLocalDate().withDayOfMonth(1);
        return d.atStartOfDay(BIZ_ZONE).toOffsetDateTime();
    }
}
