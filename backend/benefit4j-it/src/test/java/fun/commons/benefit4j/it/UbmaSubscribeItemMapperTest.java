package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaSubscribeItem;
import fun.commons.benefit4j.mapper.UbmaSubscribeItemMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class UbmaSubscribeItemMapperTest extends BaseMapperTest {

    @Autowired
    private UbmaSubscribeItemMapper mapper;

    @Test
    public void testNextRefreshTimeNullable() {
        Long tenantId = createTenant().getId();
        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setTenantId(tenantId);
        // 用雪花 id 避免 (subscribe_id, item_id, source_type=SUBSCRIPTION) 唯一约束冲突
        item.setSubscribeId(System.nanoTime());
        item.setItemId(System.nanoTime());
        item.setTotalConsumed(0);
        item.setPeriodConsumed(0);
        item.setFrozenConsumed(0);
        item.setQuotaLimit(10);
        item.setNextRefreshTime(null);
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());

        mapper.insert(item);
        UbmaSubscribeItem db = mapper.selectById(item.getId());
        assertThat(db.getNextRefreshTime()).isNull();
    }

    @Test
    public void testOptimisticLockVersion() {
        Long tenantId = createTenant().getId();
        UbmaSubscribeItem item = new UbmaSubscribeItem();
        item.setTenantId(tenantId);
        item.setSubscribeId(System.nanoTime());
        item.setItemId(System.nanoTime());
        item.setTotalConsumed(0);
        item.setPeriodConsumed(0);
        item.setFrozenConsumed(0);
        item.setQuotaLimit(10);
        item.setCreatedAt(OffsetDateTime.now());
        item.setUpdatedAt(OffsetDateTime.now());

        mapper.insert(item);

        // re-read to get DB-generated version=0
        item = mapper.selectById(item.getId());
        assertThat(item.getVersion()).isEqualTo(0);

        // update with wrapper — optimistic lock adds version check
        LambdaUpdateWrapper<UbmaSubscribeItem> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UbmaSubscribeItem::getId, item.getId())
               .eq(UbmaSubscribeItem::getVersion, item.getVersion())
               .set(UbmaSubscribeItem::getPeriodConsumed, 3)
               .set(UbmaSubscribeItem::getFrozenConsumed, 1)
               .set(UbmaSubscribeItem::getVersion, item.getVersion() + 1);
        int rows = mapper.update(null, wrapper);
        assertThat(rows).isEqualTo(1);

        // re-read to verify version incremented
        UbmaSubscribeItem updated = mapper.selectById(item.getId());
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getPeriodConsumed()).isEqualTo(3);

        // stale version should fail
        LambdaUpdateWrapper<UbmaSubscribeItem> staleWrapper = new LambdaUpdateWrapper<>();
        staleWrapper.eq(UbmaSubscribeItem::getId, item.getId())
                    .eq(UbmaSubscribeItem::getVersion, 0)
                    .set(UbmaSubscribeItem::getPeriodConsumed, 99)
                    .set(UbmaSubscribeItem::getVersion, 1);
        int staleRows = mapper.update(null, staleWrapper);
        assertThat(staleRows).isEqualTo(0);
    }
}
