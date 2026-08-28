package fun.commons.benefit4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.benefit4j.entity.UbmaBenefitItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UbmaBenefitItemMapper extends BaseMapper<UbmaBenefitItem> {

    /**
     * 平台权益项总览: 跨租户聚合 ubma_benefit_item + ubma_tenant + ubma_subscribe_item,
     * 计算 quota / used / usage_pct。
     *
     * @param tenant_id  可选租户过滤
     * @param status 可选状态过滤
     * @param keyword 可选名称/描述关键字
     */
    @Select("""
        SELECT
            bi.id                                                       AS id,
            bi.tenant_id                                                   AS tenant_id,
            tenant.name                                                    AS tenant_name,
            bi.name                                                     AS name,
            bi.icon                                                     AS icon,
            bi.description                                              AS description,
            bi.default_deduction                                         AS default_deduction,
            bi.status                                                   AS status,
            COALESCE(si.quota_limit, 0)                                  AS quota,
            COALESCE(si.total_consumed, 0)                               AS used,
            CASE WHEN COALESCE(si.quota_limit, 0) > 0
                 THEN ROUND(si.total_consumed * 100.0 / si.quota_limit)::int
                 ELSE 0 END                                              AS usage_pct,
            bi.updated_at                                               AS updated_at
        FROM ubma_benefit_item bi
        LEFT JOIN ubma_tenant tenant
               ON tenant.id = bi.tenant_id AND tenant.is_deleted = 0
        LEFT JOIN ubma_subscribe_item si
               ON si.item_id = bi.id AND si.tenant_id = bi.tenant_id AND si.is_deleted = 0
        WHERE bi.is_deleted = 0
          AND (#{tenantId}::bigint IS NULL OR bi.tenant_id = #{tenantId})
          AND (#{status}::varchar IS NULL OR bi.status = #{status})
          AND (#{keyword}::varchar IS NULL
               OR bi.name ILIKE '%' || #{keyword} || '%'
               OR bi.description ILIKE '%' || #{keyword} || '%')
        ORDER BY bi.id
        """)
    List<Map<String, Object>> selectPlatformOverview(Long tenantId, String status, String keyword);
}
