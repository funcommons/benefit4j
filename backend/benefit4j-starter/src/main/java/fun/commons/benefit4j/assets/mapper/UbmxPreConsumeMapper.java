package fun.commons.benefit4j.assets.mapper;

import fun.commons.benefit4j.assets.entity.UbmxPreConsume;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface UbmxPreConsumeMapper extends BaseMapper<UbmxPreConsume> {

    /**
     * 预扣幂等抢占: PG 事务内唯一键冲突会 abort 事务,ON CONFLICT DO NOTHING 判行数。
     */
    @Insert("/*traceid=assets,topic=pre_consume*/ "
            + "INSERT INTO ubmx_pre_consume (id, tenant_id, request_id, tx_id, charge_mode, user_account_id, "
            + "asset_code, estimated, status, expire_time) "
            + "VALUES (#{id}, #{tenantId}, #{requestId}, #{txId}, #{chargeMode}, #{userAccountId}, "
            + "#{assetCode}, #{estimated}, 'RESERVED', #{expireTime}) "
            + "ON CONFLICT (tenant_id, request_id) DO NOTHING")
    int insertIgnore(UbmxPreConsume pc);

    /**
     * 过期扫描(不加锁,终态 guard 兜底;多实例重复尝试无副作用)。
     */
    @Select("/*traceid=assets,topic=pre_expire_scan*/ "
            + "SELECT id, tenant_id, request_id, tx_id, charge_mode, user_account_id, tenant_account_id, "
            + "asset_code, estimated, settled_amount, status, expire_time, created_at, updated_at "
            + "FROM ubmx_pre_consume WHERE status = 'RESERVED' AND expire_time < CURRENT_TIMESTAMP "
            + "ORDER BY id LIMIT #{limit}")
    List<UbmxPreConsume> scanExpired(@Param("limit") int limit);

    /**
     * O15 终态竞态 guard: 只允许 RESERVED → 终态单向迁移,先抢到者赢。
     */
    @Update("/*traceid=assets,topic=pre_guard*/ "
            + "UPDATE ubmx_pre_consume SET status = #{status}, settled_amount = #{settledAmount}, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id} AND status = 'RESERVED'")
    int casToTerminal(@Param("id") Long id, @Param("status") String status,
                      @Param("settledAmount") BigDecimal settledAmount);
}
