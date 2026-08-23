package fun.commons.benefit4j.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostCacheEvictRequest {
    private List<String> cacheKeys;

    private String cacheType;
}
