package fun.commons.benefit4j.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class PostCacheEvictRequestTest {
    @Test
    public void testGetterSetter() {
        PostCacheEvictRequest req = new PostCacheEvictRequest();
        req.setCacheKeys(List.of("key1", "key2"));
        req.setCacheType("all");
        assertThat(req.getCacheKeys()).hasSize(2);
        assertThat(req.getCacheType()).isEqualTo("all");
    }
}
