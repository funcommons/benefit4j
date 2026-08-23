package fun.commons.benefit4j.service;

public interface BenefitOpsService {
    // 存活探针(检查 DB/Redis 健康度)
    Object getHealth();

    // 暴露供 Prometheus 抓取的打点数据
    Object getMetrics();

    // 紧急手动淘汰/降级失效异常缓存
    Object postCacheEvict(fun.commons.benefit4j.dto.PostCacheEvictRequest req);

    // 紧急手动触发分布式周期结转定时任务
    Object postJobsRefreshCycles(fun.commons.benefit4j.dto.PostJobsRefreshCyclesRequest req);

}
