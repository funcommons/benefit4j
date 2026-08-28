package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaSubscribe;
import fun.commons.benefit4j.mapper.UbmaSubscribeMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class UbmaSubscribeMapperTest extends BaseMapperTest {

    @Autowired
    private UbmaSubscribeMapper mapper;

    private UbmaSubscribe buildSubscribe(Long tenantId) {
        UbmaSubscribe sub = new UbmaSubscribe();
        sub.setTenantId(tenantId);
        sub.setUserid("user-001");
        sub.setSetId(10L);
        sub.setExternalOrderId("ext-sub-" + uniqueTenantid());
        sub.setFrozenConsumed(0);
        sub.setTotalConsumed(0);
        sub.setPeriodConsumed(0);
        sub.setQuotaLimit(100);
        sub.setDateBegin(OffsetDateTime.now());
        sub.setDateEnd(OffsetDateTime.now().plusMonths(1));
        sub.setStatus("ACTIVE");
        sub.setCreatedAt(OffsetDateTime.now());
        sub.setUpdatedAt(OffsetDateTime.now());
        return sub;
    }

    @Test
    public void testNewFieldsExternalOrderIdAndFrozenConsumed() {
        Long tenantId = createTenant().getId();
        UbmaSubscribe sub = buildSubscribe(tenantId);
        sub.setExternalOrderId("ext-sub-order-001");
        sub.setFrozenConsumed(2);
        sub.setPeriodConsumed(3);

        mapper.insert(sub);
        UbmaSubscribe db = mapper.selectById(sub.getId());
        assertThat(db.getExternalOrderId()).isEqualTo("ext-sub-order-001");
        assertThat(db.getFrozenConsumed()).isEqualTo(2);
    }

    @Test
    public void testNextRefreshTimeNullable() {
        Long tenantId = createTenant().getId();
        UbmaSubscribe sub = buildSubscribe(tenantId);
        sub.setNextRefreshTime(null);

        mapper.insert(sub);
        UbmaSubscribe db = mapper.selectById(sub.getId());
        assertThat(db.getNextRefreshTime()).isNull();
    }

    @Test
    public void testOptimisticLockVersion() {
        Long tenantId = createTenant().getId();
        UbmaSubscribe sub = buildSubscribe(tenantId);

        mapper.insert(sub);

        // re-read to get DB-generated version=0
        sub = mapper.selectById(sub.getId());
        assertThat(sub.getVersion()).isEqualTo(0);

        // update with wrapper — optimistic lock adds version check
        LambdaUpdateWrapper<UbmaSubscribe> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaSubscribe::getId, sub.getId())
               .eq(UbmaSubscribe::getVersion, sub.getVersion())
               .set(UbmaSubscribe::getPeriodConsumed, 10)
               .set(UbmaSubscribe::getVersion, sub.getVersion() + 1);
        int rows = mapper.update(null, wrapper);
        assertThat(rows).isEqualTo(1);

        // re-read to verify version incremented
        UbmaSubscribe updated = mapper.selectById(sub.getId());
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getPeriodConsumed()).isEqualTo(10);

        // stale version should fail
        LambdaUpdateWrapper<UbmaSubscribe> staleWrapper = new LambdaUpdateWrapper<>();
        staleWrapper.eq(UbmaSubscribe::getId, sub.getId())
                    .eq(UbmaSubscribe::getVersion, 0)
                    .set(UbmaSubscribe::getPeriodConsumed, 99)
                    .set(UbmaSubscribe::getVersion, 1);
        int staleRows = mapper.update(null, staleWrapper);
        assertThat(staleRows).isEqualTo(0);
    }

    @Test
    public void testExternalOrderIdUniqueConstraint() {
        Long tenantId = createTenant().getId();
        String extOrderId = "ext-sub-dup-" + uniqueTenantid();

        UbmaSubscribe sub1 = buildSubscribe(tenantId);
        sub1.setExternalOrderId(extOrderId);
        mapper.insert(sub1);

        UbmaSubscribe sub2 = buildSubscribe(tenantId);
        sub2.setExternalOrderId(extOrderId);

        assertThatThrownBy(() -> mapper.insert(sub2))
                .hasMessageContaining("duplicate key");
    }
}
