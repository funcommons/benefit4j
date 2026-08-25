package fun.commons.benefit4j.assets.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.benefit4j.assets.entity.UbmxTxOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UbmxTxOrderMapper extends BaseMapper<UbmxTxOrder> {

    /**
     * O10 幂等抢占: PG 事务内唯一键冲突会把事务标记 aborted(catch 后不能再执行任何 SQL),
     * 必须用 ON CONFLICT DO NOTHING 判返回行数: 1=抢占成功,0=已被占用(随后 SELECT 读旧行回放/判冲突)。
     */
    @Insert("/*traceid=assets,topic=tx_gate*/ "
            + "INSERT INTO ubmx_tx_order (id, app_id, ext_order_id, tx_type, tx_id, status) "
            + "VALUES (#{id}, #{appId}, #{extOrderId}, #{txType}, #{txId}, #{status}) "
            + "ON CONFLICT (app_id, ext_order_id) DO NOTHING")
    int insertIgnore(UbmxTxOrder gate);

    /** O6 幂等释放: SUCCESS → FAILED(仅 OPS 纠错;FAILED 键引擎侧永久禁用防双记) */
    @Update("/*traceid=assets,topic=tx_release*/ "
            + "UPDATE ubmx_tx_order SET status = 'FAILED', updated_at = CURRENT_TIMESTAMP, "
            + "result_snapshot = COALESCE(result_snapshot, '{}'::jsonb) "
            + "|| jsonb_build_object('releaseReason', #{reason}::text) "
            + "WHERE app_id = #{appId} AND ext_order_id = #{extOrderId} AND status = 'SUCCESS'")
    int releaseKey(@Param("appId") Long appId, @Param("extOrderId") String extOrderId,
                   @Param("reason") String reason);
}
