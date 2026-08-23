package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaBenefitSet;
import fun.commons.benefit4j.mapper.UbmaBenefitSetMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class UbmaBenefitSetMapperTest extends BaseMapperTest {

    @Autowired
    private UbmaBenefitSetMapper mapper;

    @Test
    public void testNewFieldsTimingModeAndQuotaUnit() {
        Long appId = createApp().getId();
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setAppId(appId);
        set.setName("VIP Monthly Card");
        set.setDuration(1);
        set.setDurationUnit("month");
        set.setPriority(10);
        set.setQuota(30);
        set.setRefreshCycle(1);
        set.setRefreshCycleUnit("day");
        set.setTimingMode("RENEWAL");
        set.setQuotaUnit("次");
        set.setStatus("ACTIVE");
        set.setCreatedAt(OffsetDateTime.now());
        set.setUpdatedAt(OffsetDateTime.now());

        mapper.insert(set);
        UbmaBenefitSet db = mapper.selectById(set.getId());
        assertThat(db.getTimingMode()).isEqualTo("RENEWAL");
        assertThat(db.getQuotaUnit()).isEqualTo("次");
    }

    @Test
    public void testTimingModeCurrentPeriod() {
        Long appId = createApp().getId();
        UbmaBenefitSet set = new UbmaBenefitSet();
        set.setAppId(appId);
        set.setName("Flash Pass");
        set.setDuration(0);
        set.setDurationUnit("day");
        set.setPriority(5);
        set.setQuota(1);
        set.setRefreshCycle(0);
        set.setRefreshCycleUnit("day");
        set.setTimingMode("CURRENT");
        set.setQuotaUnit("份");
        set.setStatus("ACTIVE");
        set.setCreatedAt(OffsetDateTime.now());
        set.setUpdatedAt(OffsetDateTime.now());

        mapper.insert(set);
        UbmaBenefitSet db = mapper.selectById(set.getId());
        assertThat(db.getTimingMode()).isEqualTo("CURRENT");
        assertThat(db.getQuotaUnit()).isEqualTo("份");
    }
}
