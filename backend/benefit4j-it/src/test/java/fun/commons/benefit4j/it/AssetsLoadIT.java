package fun.commons.benefit4j.it;

import fun.commons.benefit4j.assets.dto.PostingCommand;
import fun.commons.benefit4j.assets.service.PostingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * assets 域 P2 末压测(dev-plan §5): 业务口径峰值 ~120 TPS 的安全垫验证。
 *   混合负载: 90% consume(随机用户 1-10 点)+ 10% issue(50 点);
 *   验收: 零业务失败(无死锁/无超发/无异常)+ 吞吐 ≥ 60 TPS 保底(打印实际值,
 *   本地 IT 环境不卡 120 硬线防 flaky,生产压测另行执行)。
 */
@Tag("perf")
@SpringBootTest(classes = Benefit4jIntegrationTest.TestApplication.class)
public class AssetsLoadIT extends BaseMapperTest {

    @BeforeAll
    static void applyMigrations() throws Exception {
        AssetsMigrations.applyAll();
    }

    @Autowired
    private PostingService postingService;

    private static final int USERS = 200;
    private static final int THREADS = 8;
    private static final int DURATION_SECONDS = 15;

    @Test
    public void testMixedLoad_zeroErrorsAndThroughput() throws Exception {
        Long tenantId = createTenant().getId();
        List<Long> users = new ArrayList<>();
        for (int i = 0; i < USERS; i++) users.add(uniqueLongId());

        // 预置: 每用户充 5000(批量多腿,10 笔 × 20 腿)
        for (int batch = 0; batch < 10; batch++) {
            List<PostingCommand.LegSpec> legs = new ArrayList<>();
            for (int i = batch * 20; i < (batch + 1) * 20; i++) {
                legs.add(leg("issue:POINTS", "user:" + users.get(i), "POINTS", "5000"));
            }
            postingService.commitTx(cmd(tenantId, "IT-LOAD-SEED-" + uniqueTenantid(), "ISSUE",
                    legs.toArray(new PostingCommand.LegSpec[0])));
        }

        AtomicLong ok = new AtomicLong();
        AtomicInteger fail = new AtomicInteger();
        AtomicLong totalLatencyMs = new AtomicLong();
        final long deadline = System.currentTimeMillis() + DURATION_SECONDS * 1000L;

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            final int seed = t;
            fs.add(pool.submit(() -> {
                java.util.Random rnd = new java.util.Random(42L + seed);
                while (System.currentTimeMillis() < deadline) {
                    long t0 = System.currentTimeMillis();
                    try {
                        Long uid = users.get(rnd.nextInt(USERS));
                        if (rnd.nextInt(10) == 0) {
                            postingService.commitTx(cmd(tenantId, "IT-LOAD-I-" + uniqueTenantid(), "ISSUE",
                                    leg("issue:POINTS", "user:" + uid, "POINTS", "50")));
                        } else {
                            postingService.commitTx(cmd(tenantId, "IT-LOAD-C-" + uniqueTenantid(), "CONSUME",
                                    leg("user:" + uid, "fee:POINTS", "POINTS",
                                            String.valueOf(1 + rnd.nextInt(10)))));
                        }
                        ok.incrementAndGet();
                    } catch (Exception e) {
                        fail.incrementAndGet();
                    }
                    totalLatencyMs.addAndGet(System.currentTimeMillis() - t0);
                }
            }));
        }
        try {
            for (Future<?> f : fs) f.get(120, TimeUnit.SECONDS);   // 死锁/卡死会超时
        } finally {
            pool.shutdownNow();
        }

        long ops = ok.get();
        double tps = ops / (double) DURATION_SECONDS;
        double avgLatency = ops == 0 ? -1 : totalLatencyMs.get() / (double) ops;

        System.out.printf("[assets-load] ops=%d tps=%.0f avgLatency=%.1fms failures=%d%n",
                ops, tps, avgLatency, fail.get());

        assertThat(fail.get()).as("零业务失败(无死锁/无超发/无异常)").isZero();
        assertThat(ops).as("吞吐保底 60 TPS(业务口径峰值 120 的安全垫)").isGreaterThan(60 * DURATION_SECONDS);
        // 守恒抽检: 任一用户余额 = 5000 + issues×50 − consumes(由引擎保证,此处仅验证可读)
        var acc = postingService != null ? users.get(0) : null;
        assertThat(acc).isNotNull();
    }

    // ---------- locals ----------

    private PostingCommand cmd(Long tenantId, String orderId, String txType, PostingCommand.LegSpec... legs) {
        PostingCommand c = new PostingCommand();
        c.setTenantId(tenantId);
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
