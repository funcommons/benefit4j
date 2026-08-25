package fun.commons.benefit4j.assets.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 解冻请求: RELEASE=回余额(售后撤销)/CONSUME=出账不回(提现成功);amount=null 全额,支持部分 */
@Getter
@Setter
public class UnfreezeRequest {

    private Long appId;

    private String freezeNo;

    /** RELEASE | CONSUME */
    private String mode;

    /** 释放额,null = 剩余全部 */
    private BigDecimal amount;
}
