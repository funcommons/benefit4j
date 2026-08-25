package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.benefit4j.assets.service.AssetsFiatGuard;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 A4: 资产注册中心 CRUD + FIAT 合规 fail-fast
 * 依据 assets-design.md v0.4 §2.1 / §7.1
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class AssetRegistryServiceIT extends BaseMapperTest {

    @Autowired
    private AssetRegistryService registry;

    @Autowired
    private AssetsFiatGuard fiatGuard;

    private UbmxAsset newAsset(String code) {
        UbmxAsset a = new UbmxAsset();
        a.setCode(code);
        a.setName("测试资产-" + code);
        a.setAssetType("VIRTUAL");
        a.setPrecision(2);
        a.setExpirePolicy(Map.of("strategy", "NEVER"));
        return a;
    }

    @Test
    public void testCreateAsset_withDbDefaults() {
        String code = "T" + uniqueAppid().toUpperCase();
        UbmxAsset created = registry.createAsset(newAsset(code));

        assertThat(created.getCode()).isEqualTo(code);
        // DB 默认位生效: can_pay/can_exchange 默认 TRUE, can_credit/can_withdraw 默认 FALSE
        assertThat(created.getCanPay()).isTrue();
        assertThat(created.getCanExchange()).isTrue();
        assertThat(created.getCanCredit()).isFalse();
        assertThat(created.getCanWithdraw()).isFalse();
        assertThat(created.getStatus()).isEqualTo("ACTIVE");
        assertThat(created.getCreatedAt()).isNotNull();
        // JSONB 回读
        assertThat(created.getExpirePolicy()).isEqualTo(Map.of("strategy", "NEVER"));
    }

    @Test
    public void testCreateAsset_duplicateCodeRejected() {
        String code = "T" + uniqueAppid().toUpperCase();
        registry.createAsset(newAsset(code));
        assertThatThrownBy(() -> registry.createAsset(newAsset(code)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    public void testCreateAsset_invalidInputRejected() {
        assertThatThrownBy(() -> registry.createAsset(newAsset("bad code!")))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.ASSET_INVALID);
        UbmxAsset badPrecision = newAsset("T" + uniqueAppid().toUpperCase());
        badPrecision.setPrecision(9);
        assertThatThrownBy(() -> registry.createAsset(badPrecision))
                .isInstanceOf(AssetsException.class);
    }

    @Test
    public void testGetRequired_notFound() {
        assertThatThrownBy(() -> registry.getRequired("NO_SUCH_" + uniqueAppid()))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.ASSET_NOT_FOUND);
    }

    @Test
    public void testSuspendAndResume() {
        String code = "T" + uniqueAppid().toUpperCase();
        registry.createAsset(newAsset(code));

        registry.suspend(code);
        assertThat(registry.getRequired(code).getStatus()).isEqualTo("SUSPEND");
        // SUSPEND 资产不可用于业务路径
        assertThatThrownBy(() -> registry.getActiveRequired(code))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.ASSET_SUSPENDED);

        registry.resume(code);
        assertThat(registry.getActiveRequired(code).getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    public void testListByFilter() {
        String code = "T" + uniqueAppid().toUpperCase();
        registry.createAsset(newAsset(code));

        List<UbmxAsset> active = registry.list("VIRTUAL", "ACTIVE");
        assertThat(active).extracting(UbmxAsset::getCode).contains(code);
        // FIAT 过滤下不应出现 VIRTUAL 资产
        List<UbmxAsset> fiat = registry.list("FIAT", null);
        assertThat(fiat).extracting(UbmxAsset::getCode).doesNotContain(code);
    }

    @Test
    public void testPatchAsset_onlyAllowedFields() {
        String code = "T" + uniqueAppid().toUpperCase();
        registry.createAsset(newAsset(code));

        UbmxAsset patch = new UbmxAsset();
        patch.setName("改名-" + code);
        patch.setCanExchange(false);
        patch.setLimitPolicy(Map.of("dailyMax", "50000.00"));
        registry.patchAsset(code, patch);

        UbmxAsset reloaded = registry.getRequired(code);
        assertThat(reloaded.getName()).isEqualTo("改名-" + code);
        assertThat(reloaded.getCanExchange()).isFalse();
        assertThat(reloaded.getLimitPolicy()).isEqualTo(Map.of("dailyMax", "50000.00"));
        // 未指定字段保持不变
        assertThat(reloaded.getPrecision()).isEqualTo(2);
        assertThat(reloaded.getAssetType()).isEqualTo("VIRTUAL");
    }

    @Test
    public void testSeedAssetsLoaded() {
        assertThat(registry.getActiveRequired("POINTS").getPrecision()).isEqualTo(0);
        assertThat(registry.getActiveRequired("COMPUTE").getPrecision()).isEqualTo(4);
    }

    @Test
    public void testFiatFailFast() {
        // 当前环境 assets.fiat-allowed=false(默认): 创建 FIAT 资产被拒,提示合规
        assertThatThrownBy(() -> fiatGuard.assertCreationAllowed("FIAT"))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.FIAT_NOT_ALLOWED);
        assertThatThrownBy(() -> fiatGuard.assertCreationAllowed("FIAT"))
                .hasMessageContaining("合规");

        // 虚拟资产不受限
        fiatGuard.assertCreationAllowed("VIRTUAL");

        // 启动检查语义: 已有 N 行 FIAT && !allowed → IllegalStateException fail-fast
        assertThatThrownBy(() -> fiatGuard.checkFiatCount(1, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fiat-allowed");
        fiatGuard.checkFiatCount(0, false);  // 无 FIAT,静默通过
        fiatGuard.checkFiatCount(1, true);   // 已授权,通过
    }
}
