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
            + "WHERE app_id = #{appId} AND dst_account_id = #{accountId} AND status = 'SUCCESS' "
            + "AND created_at >= #{since}")
    java.math.BigDecimal sumInSince(@Param("appId") Long appId, @Param("accountId") Long accountId,
                                    @Param("since") java.time.OffsetDateTime since);

    /** B4 限额: 指定时点起,该账户的出账累计(src 命中) */
    @Select("/*traceid=assets,topic=limit_out*/ SELECT COALESCE(SUM(amount), 0) FROM ubmx_posting "
            + "WHERE app_id = #{appId} AND src_account_id = #{accountId} AND status = 'SUCCESS' "
            + "AND created_at >= #{since}")
    java.math.BigDecimal sumOutSince(@Param("appId") Long appId, @Param("accountId") Long accountId,
                                     @Param("since") java.time.OffsetDateTime since);
}
