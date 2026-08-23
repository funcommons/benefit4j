package fun.commons.benefit4j.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询基类 (P1-2 分页 DTO 化, 准备就绪, 接口改留独立任务)
 * <p>
 * 当前 6 个分页方法 (getSubscriptions/getConsumes/getPlatformItems/...) 用 12+ 参数,
 * 后续改签名为 {@code getSubscriptions(appId, SubscriptionQuery query)} 时使用此基类。
 */
@Getter
@Setter
public class PageQuery {
    private Integer page = 1;
    private Integer size = 20;
    private String keyword;
}
