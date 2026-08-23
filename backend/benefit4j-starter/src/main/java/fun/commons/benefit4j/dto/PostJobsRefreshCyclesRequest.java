package fun.commons.benefit4j.dto;

import fun.commons.framework4j.openid.annotation.OpenId;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PostJobsRefreshCyclesRequest {
    @NotNull
    @OpenId
    private Long appId;

    private Boolean dryRun = false;
}
