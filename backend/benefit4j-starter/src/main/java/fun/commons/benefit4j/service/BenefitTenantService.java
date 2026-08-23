package fun.commons.benefit4j.service;

public interface BenefitTenantService {
    // 创建租户级自定义权益项
    Object postBenefitItems(Long appId, fun.commons.benefit4j.dto.PostBenefitItemsRequest req);

    // 分页查询租户权益项库
    Object getBenefitItems(Long appId);

    // 查询特定权益项详情
    Object getBenefitItemsItemId(Long appId, String itemId);

    // 修改指定权益项信息
    Object putBenefitItemsItemId(Long appId, String itemId, fun.commons.benefit4j.dto.PutBenefitItemsItemIdRequest req);

    // 逻辑作废/删除指定权益项
    Object deleteBenefitItemsItemId(Long appId, String itemId);

    // 查询系统授权给本租户的可用模板
    Object getBenefitTemplates(Long appId);

    // 组合权益项创建/实例化售卖产品
    Object postBenefitSets(Long appId, fun.commons.benefit4j.dto.PostBenefitSetsRequest req);

    // 分页查询租户上架的权益产品
    Object getBenefitSets(Long appId);

    // 查询权益产品装配明细与额度
    Object getBenefitSetsSetId(Long appId, String setId);

    // 修改权益产品信息及额度配置
    Object putBenefitSetsSetId(Long appId, String setId, fun.commons.benefit4j.dto.PutBenefitSetsSetIdRequest req);

    // 逻辑下架/删除指定权益产品
    Object deleteBenefitSetsSetId(Long appId, String setId);

    // (客服能力) 穿透查阅指定用户资产大账
    Object getUsersUseridAssets(Long appId, String userid);

    // (客服能力) 穿透查询流水与订单关联
    Object getUsersUseridConsumes(Long appId, String userid);

    // (客服能力) 手工补发/开通权益产品
    Object postSubscriptions(Long appId, fun.commons.benefit4j.dto.PostSubscriptionsRequest req);

    // (客服能力) 手工封禁/停用作恶用户订阅
    Object postSubscriptionsSubscribeIdDisable(Long appId, String subscribeId, fun.commons.benefit4j.dto.PostSubscriptionsSubscribeIdDisableRequest req);

    // (客服能力) 人工补偿针对单项的额度
    Object postCompensations(Long appId, fun.commons.benefit4j.dto.PostCompensationsRequest req);

    // 多条件分页查询本租户用户订阅 (userid/setId/status/externalOrderId/keyword/dateBegin 范围/createdAt 范围)
    Object getSubscriptions(Long appId,
                            String userid,
                            String setId,
                            String status,
                            String externalOrderId,
                            String keyword,
                            java.time.OffsetDateTime dateBeginStart,
                            java.time.OffsetDateTime dateBeginEnd,
                            java.time.OffsetDateTime createdAtStart,
                            java.time.OffsetDateTime createdAtEnd,
                            Integer page,
                            Integer size);

    // 列出指定订阅下的多源额度桶 (V1.2.0)
    Object getSubscriptionsSubscribeIdItems(Long appId, String subscribeId, String itemId);

    // 多条件分页查询本租户扣减流水
    Object getConsumes(Long appId,
                       String userid,
                       String subsItemId,
                       String itemId,
                       String status,
                       String externalOrderId,
                       String keyword,
                       Integer consumeNumMin,
                       Integer consumeNumMax,
                       java.time.OffsetDateTime consumeTimeStart,
                       java.time.OffsetDateTime consumeTimeEnd,
                       Integer page,
                       Integer size);

    // 手动退减: 将 COMMITTED 流水逆向回滚到 subscribe_item, consume 置 REFUNDED
    Object postConsumesIdRefund(Long appId, String consumeId, fun.commons.benefit4j.dto.PostConsumesIdRefundRequest req);

}