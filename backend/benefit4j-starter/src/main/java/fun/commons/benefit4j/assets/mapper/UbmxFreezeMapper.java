package fun.commons.benefit4j.assets.mapper;

import fun.commons.benefit4j.assets.entity.UbmxFreeze;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UbmxFreezeMapper extends BaseMapper<UbmxFreeze> {

    /**
     * 冻结幂等抢占: PG 事务内唯一键冲突会 abort 事务(Step3 P0 教训),
     * ON CONFLICT DO NOTHING 判行数,0=已被占用(读旧行返回当前状态)。
     */
    @Insert("/*traceid=assets,topic=freeze_gate*/ "
            + "INSERT INTO ubmx_freeze (id, tenant_id, account_id, freeze_no, reason, amount, used_amount, "
            + "status, expire_time) "
            + "VALUES (#{id}, #{tenantId}, #{accountId}, #{freezeNo}, #{reason}, #{amount}, #{usedAmount}, "
            + "#{status}, #{expireTime}) "
            + "ON CONFLICT (tenant_id, freeze_no) DO NOTHING")
    int insertIgnore(UbmxFreeze row);
}
