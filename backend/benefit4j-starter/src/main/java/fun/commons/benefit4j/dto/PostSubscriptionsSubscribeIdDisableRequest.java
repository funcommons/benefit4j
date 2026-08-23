package fun.commons.benefit4j.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PostSubscriptionsSubscribeIdDisableRequest {
    @Size(max = 255, message = "reason最长255字符")
    private String reason;
}
