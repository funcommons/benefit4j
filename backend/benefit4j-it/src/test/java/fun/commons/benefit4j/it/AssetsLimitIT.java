package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.dto.PreConsumeRequest;
import fun.commons.benefit4j.assets.dto.PreConsumeView;
import fun.commons.benefit4j.assets.entity.UbmxAsset;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.service.AssetRegistryService;
import fun.commons.benefit4j.assets.service.PostingService;
import fun.commons.benefit4j.assets.service.PreConsumeService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 P2 B4: limit_policy 反洗钱限额(§2.1 O3 / §4.3.1 规则 2)
 *   单笔 singleMax / 日累计 dailyMax / 月累计 monthlyMax,口径 Asia/Shanghai 营业日;
 *   维度 = 账户;入账(checkIn,dst)/ 出账(checkOut,src);FIAT 资产强制配 limit。
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class AssetsLimitIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private PostingService postingService;

    @Autowired
    private PreConsumeService preConsumeService;

    @Autowired
    private AssetRegistryService registry;

    private Long appId;

    private Long app() {
        if (appId == null) appId = createApp().getId();
        return appId;
    }

    /** 注册带 limit_policy 的测试资产(每用例独立 code,避免累计串扰) */
    private String assetWithLimit(Map<String, Object> limit) {
        String code = ("LMT" + uniqueAppid().substring(0, 8)).toUpperCase();
        try {
            var a = new UbmxAsset();
            a.setCode(code);
            a.setName("限额-" + code);
            a.setAssetType("VIRTUAL");
            a.setPrecision(2);
            a.setLimitPolicy(limit);
            registry.createAsset(a);
        } catch (DuplicateKeyException ignore) {
            // ok
        }
        return code;
    }

    private void issue(String orderId, String asset, Long uid, String amount) {
        postingService.commitTx(cmd(orderId, "ISSUE", leg("issue:" + asset, "user:" + uid, asset, amount)));
    }

    @Test
    public void testSingleMax_rejected() {
        String asset = assetWithLimit(Map.of("singleMax", "100.00"));
        Long uid = uniqueLongId();

        issue("IT-LMT-1A-" + uniqueAppid(), asset, uid, "100.00");   // 恰好等于上限,放行
        assertThatThrownBy(() -> issue("IT-LMT-1B-" + uniqueAppid(), asset, uid, "100.01"))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.LIMIT_EXCEEDED);
    }

    @Test
    public void testDailyAccumulation() {
        String asset = assetWithLimit(Map.of("dailyMax", "200.00"));
        Long uid = uniqueLongId();

        issue("IT-LMT-2A-" + uniqueAppid(), asset, uid, "100.00");
        issue("IT-LMT-2B-" + uniqueAppid(), asset, uid, "100.00");   // 累计恰 200,放行
        assertThatThrownBy(() -> issue("IT-LMT-2C-" + uniqueAppid(), asset, uid, "0.01"))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.LIMIT_EXCEEDED);
    }

    @Test
    public void testMonthlyAccumulation() {
        // 日限放宽,月限卡死: 100 × 2 后第三次触月限
        String asset = assetWithLimit(Map.of("dailyMax", "10000.00", "monthlyMax", "250.00"));
        Long uid = uniqueLongId();

        issue("IT-LMT-3A-" + uniqueAppid(), asset, uid, "100.00");
        issue("IT-LMT-3B-" + uniqueAppid(), asset, uid, "100.00");
        issue("IT-LMT-3C-" + uniqueAppid(), asset, uid, "50.00");    // 250 恰好放行
        assertThatThrownBy(() -> issue("IT-LMT-3D-" + uniqueAppid(), asset, uid, "0.01"))   // 250.01 > 250
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.LIMIT_EXCEEDED);
    }

    @Test
    public void testConsumeSide_outAccumulation() {
        // 入账大额放行(扁平 in),出账侧 out 方向键卡日累计
        String asset = assetWithLimit(Map.of(
                "singleMax", "10000.00",
                "out", Map.of("dailyMax", "150.00")));
        Long uid = uniqueLongId();
        issue("IT-LMT-5F-" + uniqueAppid(), asset, uid, "1000.00");

        // 出账 100 + 50 累计 150 恰好;再 0.01 拒(src 侧日累计)
        postingService.commitTx(cmd("IT-LMT-5A-" + uniqueAppid(), "CONSUME",
                leg("user:" + uid, "fee:" + asset, asset, "100.00")));
        postingService.commitTx(cmd("IT-LMT-5B-" + uniqueAppid(), "CONSUME",
                leg("user:" + uid, "fee:" + asset, asset, "50.00")));
        assertThatThrownBy(() -> postingService.commitTx(cmd("IT-LMT-5C-" + uniqueAppid(), "CONSUME",
                leg("user:" + uid, "fee:" + asset, asset, "0.01"))))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.LIMIT_EXCEEDED);
    }

    @Test
    public void testPreConsume_estimatedChecked() {
        String asset = assetWithLimit(Map.of(
                "singleMax", "10000.00",
                "out", Map.of("singleMax", "100.00")));
        Long uid = uniqueLongId();
        issue("IT-LMT-6F-" + uniqueAppid(), asset, uid, "1000.00");   // 入账放行

        PreConsumeRequest pre = new PreConsumeRequest();
        pre.setAppId(app());
        pre.setRequestId("IT-LMT-6-" + uniqueAppid());
        pre.setAccountRef("user:" + uid);
        pre.setAssetCode(asset);
        pre.setEstimated(new BigDecimal("150.00"));
        assertThatThrownBy(() -> preConsumeService.preConsume(pre))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.LIMIT_EXCEEDED);
    }

    @Test
    public void testNoLimitPolicy_noInterception() {
        // POINTS 种子无 limit_policy,任意金额不拦
        Long uid = uniqueLongId();
        issue("IT-LMT-7-" + uniqueAppid(), "POINTS", uid, "999999");
        assertThat(postingService).isNotNull();
    }

    // ---------- locals ----------

    private PostingCommand cmd(String orderId, String txType, PostingCommand.LegSpec... legs) {
        PostingCommand c = new PostingCommand();
        c.setAppId(app());
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(List.of(legs));
        return c;
    }

    private PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        PostingCommand.LegSpec l = new PostingCommand.LegSpec();
        l.setSrc(src);
        l.setDst(dst);
        l.setAssetCode(asset);
        l.setAmount(new BigDecimal(amount));
        return l;
    }
}
