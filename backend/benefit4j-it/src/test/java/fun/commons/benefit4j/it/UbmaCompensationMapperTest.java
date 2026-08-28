package fun.commons.benefit4j.it;

import fun.commons.benefit4j.entity.UbmaCompensation;
import fun.commons.benefit4j.mapper.UbmaCompensationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

public class UbmaCompensationMapperTest extends BaseMapperTest {

    @Autowired
    private UbmaCompensationMapper mapper;

    @Test
    public void testInsertAndSelect() {
        Long tenantId = createTenant().getId();
        UbmaCompensation comp = new UbmaCompensation();
        comp.setTenantId(tenantId);
        comp.setSubscribeId(100L);
        comp.setSubsItemId(200L);
        comp.setItemId(300L);
        comp.setAdjustNum(5);
        comp.setAdjustType("ADD");
        comp.setReason("customer complaint");
        comp.setOperator("cs-agent-001");
        comp.setCreatedAt(OffsetDateTime.now());
        comp.setUpdatedAt(OffsetDateTime.now());

        int result = mapper.insert(comp);
        assertThat(result).isEqualTo(1);
        assertThat(comp.getId()).isNotNull();

        UbmaCompensation db = mapper.selectById(comp.getId());
        assertThat(db).isNotNull();
        assertThat(db.getTenantId()).isEqualTo(tenantId);
        assertThat(db.getAdjustNum()).isEqualTo(5);
        assertThat(db.getAdjustType()).isEqualTo("ADD");
        assertThat(db.getOperator()).isEqualTo("cs-agent-001");
    }

    @Test
    public void testReduceAdjustType() {
        Long tenantId = createTenant().getId();
        UbmaCompensation comp = new UbmaCompensation();
        comp.setTenantId(tenantId);
        comp.setSubscribeId(101L);
        comp.setSubsItemId(201L);
        comp.setItemId(301L);
        comp.setAdjustNum(3);
        comp.setAdjustType("REDUCE");
        comp.setReason("over-credited");
        comp.setOperator("cs-agent-002");
        comp.setCreatedAt(OffsetDateTime.now());
        comp.setUpdatedAt(OffsetDateTime.now());

        mapper.insert(comp);
        UbmaCompensation db = mapper.selectById(comp.getId());
        assertThat(db.getAdjustType()).isEqualTo("REDUCE");
        assertThat(db.getAdjustNum()).isEqualTo(3);
    }
}
