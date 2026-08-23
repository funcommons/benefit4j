package fun.commons.benefit4j.client.impl;

import fun.commons.benefit4j.client.BenefitOpsClient;
import fun.commons.benefit4j.service.BenefitOpsService;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocalBenefitOpsClient implements BenefitOpsClient {

    private final BenefitOpsService service;

    @Override
    public Object getHealth() {
        return service.getHealth();
    }

    @Override
    public Object getMetrics() {
        return service.getMetrics();
    }

    @Override
    public Object postCacheEvict(fun.commons.benefit4j.dto.PostCacheEvictRequest req) {
        return service.postCacheEvict(req);
    }

    @Override
    public Object postJobsRefreshCycles(fun.commons.benefit4j.dto.PostJobsRefreshCyclesRequest req) {
        return service.postJobsRefreshCycles(req);
    }

}
