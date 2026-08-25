package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.service.AccountService;
import fun.commons.benefit4j.assets.service.PostingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * assets 域 A9: 并发压测(设计 §9.2 / P1 DoD)
 *   1) 100 线程并发同账户扣减,无超发(终态守恒);
 *   2) 并发 issue 共享同一边界户,无死锁(O11 豁免锁的直接验证);
 *   3) DB CHECK 兜底: 绕过应用直接 SQL 写负余额被拒(资金安全最后一道闸)。
 */
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class AssetsConcurrencyIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private PostingService postingService;

    @Autowired
    private AccountService accountService;

    private Long appId;

    private Long app() {
        if (appId == null) appId = createApp().getId();
        return appId;
    }

    @Test
    public void test100ThreadsSameAccount_noOverIssue() throws Exception {
        Long uid = uniqueLongId();
        // 预充 600: 100 线程 × 5.5 = 550,余 50
        postingService.commitTx(cmd("IT-CONC-PREP-" + uniqueAppid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "600")));

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final int seq = i;
                fs.add(pool.submit(() -> {
                    try {
                        postingService.commitTx(cmd("IT-CONC-" + seq + "-" + uniqueAppid(), "CONSUME",
                                leg("user:" + uid, "fee:POINTS", "POINTS", "5.5")));
                        ok.incrementAndGet();
                    } catch (Exception e) {
                        rejected.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : fs) f.get(120, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // 无超发: 全部成功(余额足够),终态 = 600 - 550 = 50
        assertThat(ok.get()).isEqualTo(threads);
        assertThat(rejected.get()).isZero();
        BigDecimal balance = accountService
                .getOrCreateAccount(app(), "USER", uid, "POINTS").getBalance();
        assertThat(balance).isEqualByComparingTo("50");
    }

    @Test
    public void testConcurrentIssueSharedBoundary_noDeadlock() throws Exception {
        // 32 线程 × 20 笔 issue,src 全部是同一边界户 issue:GOLD(O11: 不锁不记账 → 不应串行卡死)
        int threads = 32;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger ok = new AtomicInteger();
        List<Future<?>> fs = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        try {
            for (int i = 0; i < threads; i++) {
                fs.add(pool.submit(() -> {
                    for (int j = 0; j < perThread; j++) {
                        postingService.commitTx(cmd("IT-BND-" + uniqueAppid(), "ISSUE",
                                leg("issue:GOLD", "user:" + uniqueLongId(), "GOLD", "1")));
                        ok.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : fs) f.get(120, TimeUnit.SECONDS);   // 死锁会超时
        } finally {
            pool.shutdownNow();
        }
        long costMs = System.currentTimeMillis() - t0;

        assertThat(ok.get()).isEqualTo(threads * perThread);   // 640 笔全部成功
        // 边界户 balance 恒 0(O11: 从不更新)
        assertThat(accountService.getOrCreateBoundaryAccount(app(), "issue:GOLD", "GOLD").getBalance())
                .isEqualByComparingTo("0");
        // 吞吐合理性: 640 笔 < 60s(均值 >10 TPS 即远超业务口径 12 TPS 的安全垫)
        assertThat(costMs).isLessThan(60_000L);
    }

    @Test
    public void testDbCheckRejectsNegativeDirectSql() throws Exception {
        Long uid = uniqueLongId();
        postingService.commitTx(cmd("IT-CHK-" + uniqueAppid(), "ISSUE",
                leg("issue:POINTS", "user:" + uid, "POINTS", "10")));
        Long accId = accountService.getOrCreateAccount(app(), "USER", uid, "POINTS").getId();

        // 绕过应用层直接写负余额 → DB CHECK 拒绝(SQLState 23514)
        String url = System.getProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/benefit4j");
        try (Connection conn = DriverManager.getConnection(url, "admin", "test@2026");
             var stmt = conn.createStatement()) {
            boolean rejected = false;
            try {
                stmt.executeUpdate("UPDATE ubmx_account SET balance = -1 WHERE id = " + accId);
            } catch (Exception e) {
                rejected = true;
            }
            assertThat(rejected).as("DB CHECK must reject negative balance even via direct SQL").isTrue();
        }
    }

    // ---------- locals ----------

    private fun.commons.benefit4j.assets.dto.PostingCommand cmd(String orderId, String txType,
            fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec... legs) {
        fun.commons.benefit4j.assets.dto.PostingCommand c = new fun.commons.benefit4j.assets.dto.PostingCommand();
        c.setAppId(app());
        c.setExtOrderId(orderId);
        c.setTxType(txType);
        c.setLegs(List.of(legs));
        return c;
    }

    private fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec leg(String src, String dst, String asset, String amount) {
        fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec l = new fun.commons.benefit4j.assets.dto.PostingCommand.LegSpec();
        l.setSrc(src); l.setDst(dst); l.setAssetCode(asset); l.setAmount(new BigDecimal(amount));
        return l;
    }
}
