package fun.commons.benefit4j.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 流水归档请求 (ubma_consume 分区表旧数据归档/清理)
 */
@Getter
@Setter
public class PostJobsArchiveConsumesRequest {
    /** 归档此日期之前的流水 (ISO-8601, 如 2026-06-01T00:00:00Z) */
    private String beforeDate;

    /** 可选: 按 app_id 归档 (null=全量) */
    private String appId;

    /** dry-run 模式 (仅统计, 不实际删除) */
    private Boolean dryRun;
}
