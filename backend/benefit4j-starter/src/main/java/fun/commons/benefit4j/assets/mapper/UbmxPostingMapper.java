package fun.commons.benefit4j.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.benefit4j.assets.entity.UbmxPosting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UbmxPostingMapper extends BaseMapper<UbmxPosting> {

    /** B4 限额: 指定时点起,该账户的入账累计(dst 命中,含 SUCCESS 腿) */
    @Select("/*traceid=assets,topic=limit_in*/ SELECT COALESCE(SUM(amount), 0) FROM ubmx_posting "
            + "WHERE tenant_id = #{tenantId} AND dst_account_id = #{accountId} AND status = 'SUCCESS' "
            + "AND created_at >= #{since}")
    java.math.BigDecimal sumInSince(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                                    @Param("since") java.time.OffsetDateTime since);

    /** B4 限额: 指定时点起,该账户的出账累计(src 命中) */
    @Select("/*traceid=assets,topic=limit_out*/ SELECT COALESCE(SUM(amount), 0) FROM ubmx_posting "
            + "WHERE tenant_id = #{tenantId} AND src_account_id = #{accountId} AND status = 'SUCCESS' "
            + "AND created_at >= #{since}")
    java.math.BigDecimal sumOutSince(@Param("tenantId") Long tenantId, @Param("accountId") Long accountId,
                                     @Param("since") java.time.OffsetDateTime since);

    /** B6: 记账事务内写 outbox 事件(复用权益域 ubmp_outbox) */
    @org.apache.ibatis.annotations.Insert("/*traceid=assets,topic=outbox*/ "
            + "INSERT INTO ubmp_outbox (id, aggregate_type, aggregate_id, event_type, payload) "
            + "VALUES (#{id}, 'assets_tx', #{txId}, 'ASSETS_TX_COMMITTED', #{payload}::jsonb)")
    int insertOutbox(@Param("id") Long id, @Param("txId") Long txId, @Param("payload") String payload);
}
