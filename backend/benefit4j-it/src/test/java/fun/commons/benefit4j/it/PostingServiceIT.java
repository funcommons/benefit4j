package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.dto.PostingResult;
import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.entity.UbmxPosting;
import fun.commons.benefit4j.assets.exception.AssetsException;
import fun.commons.benefit4j.assets.mapper.UbmxPostingMapper;
import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.PostingService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * assets 域 A5+A6: 复式记账引擎(O10 幂等闸 / O11 边界户豁免 / O12 行锁定案)
 * 依据 assets-design.md v0.4 §3.1
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class PostingServiceIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private fun.commons.benefit4j.assets.service.AssetRegistryService registry;

    @Autowired
    private UbmxPostingMapper postingMapper;

    /** 用例级临时资产,已注册则忽略 */
    private void ensureAsset(String code) {
        try {
            fun.commons.benefit4j.assets.entity.UbmxAsset a = new fun.commons.benefit4j.assets.entity.UbmxAsset();
            a.setCode(code);
            a.setName("测试-" + code);
            a.setAssetType("VIRTUAL");
            a.setPrecision(2);
            if ("TRN_T".equals(code)) a.setCanTransfer(true);
            registry.createAsset(a);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已注册
        }
    }

    private Long appId;

    private Long app() {
        if (appId == null) appId = createApp().getId();
        return appId;
    }

    private PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        PostingCommand.LegSpec l = new PostingCommand.LegSpec();
        l.setSrc(src);
        l.setDst(dst);
        l.setAssetCode(asset);
        l.setAmount(new BigDecimal(amount));
        return l;
    }

    private PostingCommand cmd(String orderId, String txType, PostingCommand.LegSpec... legs) {
        PostingCommand c = new PostingCommand();
        c.setAppId(app());
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(List.of(legs));
        return c;
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountService.getAccount(accountId).getBalance();
    }

    private List<UbmxPosting> postingsOf(String orderId) {
        return postingMapper.selectList(new LambdaQueryWrapper<UbmxPosting>()
                .eq(UbmxPosting::getAppId, app())
                .eq(UbmxPosting::getExtOrderId, orderId));
    }

    @Test
    public void testIssueSingleLeg_boundaryNotLockedOrUpdated() {
        String order = "IT-ISSUE-" + uniqueAppid();
        Long uid = uniqueLongId();

        PostingResult r = postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "100")));

        assertThat(r.getTxId()).isNotNull();
        // 用户户入账 100
        UbmxAccount user = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        assertThat(user.getBalance()).isEqualByComparingTo("100");
        // 边界户不更新 balance(O11),恒 0
        UbmxAccount issue = accountService.getOrCreateBoundaryAccount(app(), "issue:POINTS", "POINTS");
        assertThat(issue.getAccountType()).isEqualTo("BOUNDARY");
        assertThat(issue.getBalance()).isEqualByComparingTo("0");
        // posting 恰 1 腿,SUCCESS
        List<UbmxPosting> legs = postingsOf(order);
        assertThat(legs).hasSize(1);
        assertThat(legs.get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(legs.get(0).getSrcAccountId()).isEqualTo(issue.getId());
        assertThat(legs.get(0).getDstAccountId()).isEqualTo(user.getId());
        assertThat(legs.get(0).getBalanceAfter()).isEqualByComparingTo("100");
    }

    @Test
    public void testIdempotentReplay_sameResultNoDoublePosting() {
        String order = "IT-IDEM-" + uniqueAppid();
        Long uid = uniqueLongId();
        PostingCommand c = cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "50"));

        PostingResult first = postingService.commitTx(c);
        PostingResult second = postingService.commitTx(c);

        // O10: 同参重放返回同一结果(txId 相同),不重复记账
        assertThat(second.getTxId()).isEqualTo(first.getTxId());
        assertThat(balanceOf(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getId()))
                .isEqualByComparingTo("50");
        assertThat(postingsOf(order)).hasSize(1);
    }

    @Test
    public void testIdempotencyConflict_sameKeyDifferentPayload() {
        String order = "IT-CONF-" + uniqueAppid();
        Long uid = uniqueLongId();
        postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10")));

        // 同 key 异参 → 拒绝,防幂等键挪用
        assertThatThrownBy(() -> postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "999"))))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.IDEMPOTENCY_CONFLICT);
    }

    @Test
    public void testMultiLegConsume_atomic() {
        ensureAsset("CNY_T");
        String order1 = "IT-CHG-" + uniqueAppid();
        String order2 = "IT-PAY-" + uniqueAppid();
        Long uid = uniqueLongId();
        Long mid = uniqueLongId();

        // 预充值: 25 CNY + 100 POINTS(通过 issue)
        postingService.commitTx(cmd(order1, "ISSUE",
                leg("issue:CNY_T", "user:" + uid, "CNY_T", "25"),
                leg("issue:POINTS", "user:" + uid, "POINTS", "100")));

        // 组合支付: 积分抵5 + CNY 23 + 手续费2(3 腿单事务)
        postingService.commitTx(cmd(order2, "CONSUME",
                leg("user:" + uid, "issue:POINTS", "POINTS", "5"),
                leg("user:" + uid, "merchant:" + mid, "CNY_T", "23"),
                leg("user:" + uid, "fee:CNY_T", "CNY_T", "2")));

        assertThat(balanceOf(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getId()))
                .isEqualByComparingTo("95");
        assertThat(balanceOf(accountService.getOrCreateAccount(app(), "USER", uid, "CNY_T").getId()))
                .isEqualByComparingTo("0");
        assertThat(balanceOf(accountService.getOrCreateAccount(app(), "MERCHANT", mid, "CNY_T").getId()))
                .isEqualByComparingTo("23");
        assertThat(postingsOf(order2)).hasSize(3);
    }

    @Test
    public void testInsufficientBalance_allOrNothing() {
        String order1 = "IT-FILL-" + uniqueAppid();
        String order2 = "IT-FAIL-" + uniqueAppid();
        Long uid = uniqueLongId();
        postingService.commitTx(cmd(order1, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10")));

        // 第二腿超余额 → 整笔回滚: 第一腿也不生效
        assertThatThrownBy(() -> postingService.commitTx(cmd(order2, "CONSUME",
                leg("user:" + uid, "issue:POINTS", "POINTS", "5"),
                leg("user:" + uid, "issue:POINTS", "POINTS", "99"))))
                .isInstanceOf(AssetsException.class)
                .extracting(e -> ((AssetsException) e).getCode())
                .isEqualTo(AssetsException.INSUFFICIENT_BALANCE);

        assertThat(balanceOf(accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getId()))
                .isEqualByComparingTo("10");   // 原封不动
        assertThat(postingsOf(order2)).isEmpty();  // 无残留腿
    }

    @Test
    public void testCreditLimitExtendsFloor() {
        ensureAssetWithCredit();   // can_credit=true 专用资产
        String asset = "CRD_T";
        String order = "IT-CRED-" + uniqueAppid();
        Long uid = uniqueLongId();
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, asset);
        accountService.updateCreditLimit(app(), acc.getId(), new BigDecimal("10"));

        // 余额 0 + 授信 10 → 可扣 10.5? 不行;扣 10 可以
        assertThatThrownBy(() -> postingService.commitTx(cmd(order + "X", "CONSUME",
                leg("user:" + uid, "fee:" + asset, asset, "10.5"))))
                .isInstanceOf(AssetsException.class);
        postingService.commitTx(cmd(order, "CONSUME",
                leg("user:" + uid, "fee:" + asset, asset, "10")));
        assertThat(balanceOf(acc.getId())).isEqualByComparingTo("-10");
    }

    /** can_credit=true 专用资产(B7 后 POINTS 不允许授信) */
    private void ensureAssetWithCredit() {
        try {
            fun.commons.benefit4j.assets.entity.UbmxAsset a = new fun.commons.benefit4j.assets.entity.UbmxAsset();
            a.setCode("CRD_T");
            a.setName("授信测试");
            a.setAssetType("VIRTUAL");
            a.setPrecision(2);
            a.setCanCredit(true);
            registry.createAsset(a);
        } catch (org.springframework.dao.DuplicateKeyException ignore) {
            // 已注册
        }
    }

    @Test
    public void testVersionIncrementsAsAudit() {
        String order = "IT-VER-" + uniqueAppid();
        Long uid = uniqueLongId();
        postingService.commitTx(cmd(order, "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "1")));
        UbmxAccount acc = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS");
        int v1 = acc.getVersion();
        postingService.commitTx(cmd(order + "2", "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "1")));
        assertThat(accountService.getAccount(acc.getId()).getVersion()).isEqualTo(v1 + 1);
    }

    @Test
    public void testConcurrentTransfers_noDeadlock() throws Exception {
        // B7 后 TRANSFER 需 can_transfer=true(POINTS 种子为 false),用专用资产
        ensureAsset("TRN_T");
        Long a = uniqueLongId();
        Long b = uniqueLongId();
        postingService.commitTx(cmd("IT-DL-PREP-" + uniqueAppid(), "ISSUE",
                leg("issue:TRN_T", "user:" + a, "TRN_T", "100"),
                leg("issue:TRN_T", "user:" + b, "TRN_T", "100")));

        // 双向互转 20 轮:A→B 与 B→A 并发,按 id 升序锁应无死锁
        int rounds = 20;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger ok = new AtomicInteger();
        try {
            List<Future<?>> fs = List.of(
                    pool.submit(() -> transferLoop(rounds, a, b, ok)),
                    pool.submit(() -> transferLoop(rounds, b, a, ok)));
            for (Future<?> f : fs) f.get(60, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertThat(ok.get()).isEqualTo(rounds * 2);
        // 守恒: 总额 200 不变
        BigDecimal total = balanceOf(accountService.getOrCreateAccount(app(), "USER", a, "TRN_T").getId())
                .add(balanceOf(accountService.getOrCreateAccount(app(), "USER", b, "TRN_T").getId()));
        assertThat(total).isEqualByComparingTo("200");
    }

    private void transferLoop(int rounds, Long from, Long to, AtomicInteger ok) {
        for (int i = 0; i < rounds; i++) {
            postingService.commitTx(cmd("IT-DL-" + from + "-" + i + "-" + uniqueAppid(), "TRANSFER",
                    leg("user:" + from, "user:" + to, "TRN_T", "1")));
            ok.incrementAndGet();
        }
    }
}
