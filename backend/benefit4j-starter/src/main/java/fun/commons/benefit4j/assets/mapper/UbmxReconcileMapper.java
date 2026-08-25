package fun.commons.benefit4j.assets.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** B5 对账专用查询(恒等式/快照/冻结不一致/差错池),表间 JOIN 属对账域例外 */
@Mapper
public interface UbmxReconcileMapper {

    /** 恒等式期望值(O11 口径): Σ(BOUNDARY→NORMAL) − Σ(NORMAL→BOUNDARY),SUCCESS 腿 */
    @Select("/*traceid=assets,topic=reconcile_expect*/ "
            + "SELECT COALESCE(SUM(CASE WHEN sa.account_type = 'BOUNDARY' AND da.account_type = 'NORMAL' "
            + "THEN p.amount ELSE 0 END), 0) "
            + " - COALESCE(SUM(CASE WHEN sa.account_type = 'NORMAL' AND da.account_type = 'BOUNDARY' "
            + "THEN p.amount ELSE 0 END), 0) "
            + "FROM ubmx_posting p "
            + "JOIN ubmx_account sa ON sa.id = p.src_account_id "
            + "JOIN ubmx_account da ON da.id = p.dst_account_id "
            + "WHERE p.status = 'SUCCESS' AND p.app_id = #{appId} AND p.asset_code = #{assetCode}")
    BigDecimal expectedNormalBalance(@Param("appId") Long appId, @Param("assetCode") String assetCode);

    /** 实际值: NORMAL 户余额合计 */
    @Select("/*traceid=assets,topic=reconcile_actual*/ "
            + "SELECT COALESCE(SUM(balance), 0) FROM ubmx_account "
            + "WHERE app_id = #{appId} AND asset_code = #{assetCode} AND account_type = 'NORMAL'")
    BigDecimal actualNormalBalance(@Param("appId") Long appId, @Param("assetCode") String assetCode);

    /** O14 全量: frozen 与 ΣACTIVE 冻结明细不一致的账户 */
    @Select("/*traceid=assets,topic=reconcile_freeze*/ "
            + "SELECT a.id, a.frozen, COALESCE((SELECT SUM(f.amount - f.used_amount) FROM ubmx_freeze f "
            + "WHERE f.account_id = a.id AND f.status = 'ACTIVE'), 0) AS expected "
            + "FROM ubmx_account a WHERE a.app_id = #{appId} AND a.account_type = 'NORMAL' "
            + "AND a.frozen <> COALESCE((SELECT SUM(f.amount - f.used_amount) FROM ubmx_freeze f "
            + "WHERE f.account_id = a.id AND f.status = 'ACTIVE'), 0)")
    List<java.util.Map<String, Object>> freezeMismatches(@Param("appId") Long appId);

    /** 快照落盘(重跑覆盖为最新口径) */
    @Insert("/*traceid=assets,topic=reconcile_snapshot*/ "
            + "INSERT INTO ubmx_balance_snapshot (account_id, snap_date, balance, frozen) "
            + "SELECT id, CURRENT_DATE, balance, frozen FROM ubmx_account "
            + "WHERE app_id = #{appId} AND account_type = 'NORMAL' "
            + "ON CONFLICT (account_id, snap_date) DO UPDATE "
            + "SET balance = EXCLUDED.balance, frozen = EXCLUDED.frozen")
    int writeSnapshot(@Param("appId") Long appId);

    @Insert("/*traceid=assets,topic=reconcile_diff*/ "
            + "INSERT INTO ubmx_reconcile_diff (app_id, reconcile_type, asset_code, account_id, expected, actual) "
            + "VALUES (#{appId}, #{type}, #{assetCode}, #{accountId}, #{expected}, #{actual})")
    int insertDiff(@Param("appId") Long appId, @Param("type") String type,
                   @Param("assetCode") String assetCode, @Param("accountId") Long accountId,
                   @Param("expected") BigDecimal expected, @Param("actual") BigDecimal actual);
}
