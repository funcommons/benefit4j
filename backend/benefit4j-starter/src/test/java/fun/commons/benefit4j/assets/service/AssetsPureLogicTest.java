package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.exception.AssetsException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域纯逻辑单元测试(无 Spring/DB): 边界户编码表 + 限额日界口径。
 * 覆盖 IT 难以触达的时区边界(Asia/Shanghai 营业日口径是法币合规要求)。
 */
class AssetsPureLogicTest {

    // ---------- 边界户 owner_id 编码表(AccountService) ----------

    @Test
    void boundaryOwnerIds_areStable() {
        assertThat(AccountService.boundaryOwnerIdOf("issue")).isEqualTo(1L);
        assertThat(AccountService.boundaryOwnerIdOf("fee")).isEqualTo(2L);
        assertThat(AccountService.boundaryOwnerIdOf("exchange")).isEqualTo(3L);
        assertThat(AccountService.boundaryOwnerIdOf("credit")).isEqualTo(4L);
        assertThat(AccountService.boundaryOwnerIdOf("world:wechat")).isEqualTo(101L);
        assertThat(AccountService.boundaryOwnerIdOf("world:alipay")).isEqualTo(102L);
        assertThat(AccountService.boundaryOwnerIdOf("world:union")).isEqualTo(103L);
    }

    @Test
    void unknownBoundaryRef_rejected() {
        assertThatThrownBy(() -> AccountService.boundaryOwnerIdOf("world:paypal"))
                .isInstanceOf(AssetsException.class)
                .hasMessageContaining("world:paypal");
        assertThatThrownBy(() -> AccountService.boundaryOwnerIdOf("nonsense"))
                .isInstanceOf(AssetsException.class);
    }

    // ---------- 限额日界(AssetsLimitGuard,Asia/Shanghai 营业日) ----------

    @Test
    void dayStart_isShanghaiMidnight() {
        // 2026-08-25 23:30 UTC = 2026-08-26 07:30 上海 → 日界应为上海 08-26 00:00
        OffsetDateTime utcLateNight = ZonedDateTime.of(2026, 8, 25, 23, 30, 0, 0, ZoneId.of("UTC"))
                .toOffsetDateTime();
        OffsetDateTime start = AssetsLimitGuard.dayStart(utcLateNight);
        assertThat(start.atZoneSameInstant(AssetsLimitGuard.BIZ_ZONE).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 8, 26));
        assertThat(start.atZoneSameInstant(AssetsLimitGuard.BIZ_ZONE).toLocalTime().getHour()).isZero();
    }

    @Test
    void monthStart_isFirstDayOfShanghaiMonth() {
        // 2026-12-31 20:00 UTC = 2027-01-01 04:00 上海 → 月界为上海 2027-01-01 00:00(跨年)
        OffsetDateTime newYearEve = ZonedDateTime.of(2026, 12, 31, 20, 0, 0, 0, ZoneId.of("UTC"))
                .toOffsetDateTime();
        OffsetDateTime start = AssetsLimitGuard.monthStart(newYearEve);
        assertThat(start.atZoneSameInstant(AssetsLimitGuard.BIZ_ZONE).toLocalDate())
                .isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void dayStart_monthBoundary() {
        // 上海 2026-09-01 00:05 → 日界=月界=09-01 00:00
        OffsetDateTime justAfter = ZonedDateTime.of(2026, 9, 1, 0, 5, 0, 0, AssetsLimitGuard.BIZ_ZONE)
                .toOffsetDateTime();
        assertThat(AssetsLimitGuard.dayStart(justAfter))
                .isEqualTo(AssetsLimitGuard.monthStart(justAfter));
    }

    // ---------- 错误码常量稳定性(前端按 code 分流,不可漂移) ----------

    @Test
    void errorCodeConstants_areStable() {
        assertThat(AssetsException.INSUFFICIENT_BALANCE).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(AssetsException.IDEMPOTENCY_CONFLICT).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(AssetsException.LIMIT_EXCEEDED).isEqualTo("LIMIT_EXCEEDED");
        assertThat(AssetsException.FIAT_NOT_ALLOWED).isEqualTo("FIAT_NOT_ALLOWED");
        assertThat(AssetsException.PRE_CONSUME_NOT_FOUND).isEqualTo("PRE_CONSUME_NOT_FOUND");
        assertThat(AssetsException.FREEZE_NOT_FOUND).isEqualTo("FREEZE_NOT_FOUND");
        AssetsException ex = new AssetsException(AssetsException.ASSET_INVALID, "msg");
        assertThat(ex.getCode()).isEqualTo("ASSET_INVALID");
    }
}
