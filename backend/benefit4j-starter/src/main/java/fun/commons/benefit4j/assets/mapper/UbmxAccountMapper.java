package fun.commons.benefit4j.assets.mapper;

import fun.commons.benefit4j.assets.entity.UbmxAccount;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Mapper
public interface UbmxAccountMapper extends BaseMapper<UbmxAccount> {

    /**
     * O12: 引擎行锁 —— 仅 NORMAL 户按 id 升序 FOR UPDATE(防死锁);
     * 边界户不进锁集合(O11)。
     */
    @Select("<script>"
            + "/*traceid=assets,topic=posting_lock*/ "
            + "SELECT id, app_id, owner_type, owner_id, asset_code, account_type, balance, "
            + "credit_limit, frozen, version, status, ext, created_at, updated_at "
            + "FROM ubmx_account WHERE id IN "
            + "<foreach collection='ids' item='i' open='(' separator=',' close=')'>#{i}</foreach> "
            + "ORDER BY id FOR UPDATE"
            + "</script>")
    List<UbmxAccount> lockByIds(@Param("ids") Collection<Long> ids);

    /**
     * 引擎记账: balance ± delta + version+1(纯审计,O12 不参与 WHERE)。
     * 负余额由 DB CHECK(account_type='BOUNDARY' OR balance >= -credit_limit)最后兜底。
     */
    @Update("/*traceid=assets,topic=posting_adjust*/ "
            + "UPDATE ubmx_account SET balance = balance + #{delta}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int adjustBalance(@Param("id") Long id, @Param("delta") BigDecimal delta);

    /**
     * TCC 预扣专用: balance 与 frozen 原子同动(预扣 -b/+f,结算/退款反向),
     * 总资产不变,负余额仍由 DB CHECK 兜底。
     */
    @Update("/*traceid=assets,topic=account_freeze*/ "
            + "UPDATE ubmx_account SET balance = balance + #{balanceDelta}, "
            + "frozen = frozen + #{frozenDelta}, version = version + 1, "
            + "updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int adjustBalanceAndFrozen(@Param("id") Long id,
                               @Param("balanceDelta") BigDecimal balanceDelta,
                               @Param("frozenDelta") BigDecimal frozenDelta);

    /**
     * lazy 开户: 并发下撞 (app,owner,asset) 唯一键不得中断事务(PG aborted 语义),
     * ON CONFLICT DO NOTHING 后重读。id 由调用方 IdWorker 生成(自定义 SQL 不走 MP 填充);
     * extJson 由调用方序列化(连接串 stringtype=unspecified,String 可直写 jsonb)。
     */
    @Insert("/*traceid=assets,topic=account_open*/ "
            + "INSERT INTO ubmx_account (id, app_id, owner_type, owner_id, asset_code, account_type, "
            + "balance, credit_limit, frozen, version, status, ext) "
            + "VALUES (#{acc.id}, #{acc.appId}, #{acc.ownerType}, #{acc.ownerId}, #{acc.assetCode}, "
            + "#{acc.accountType}, #{acc.balance}, #{acc.creditLimit}, #{acc.frozen}, 0, #{acc.status}, #{extJson}) "
            + "ON CONFLICT DO NOTHING")
    int insertIgnore(@Param("acc") UbmxAccount acc, @Param("extJson") String extJson);
}
