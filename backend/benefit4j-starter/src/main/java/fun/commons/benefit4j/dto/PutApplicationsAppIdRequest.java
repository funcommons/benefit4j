package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PutApplicationsAppIdRequest {
    @Size(max = 64, message = "name最长64字符")
    private String name;

    @Size(max = 512, message = "description最长512字符")
    private String description;

    private String status;

    private Object ext;
}
