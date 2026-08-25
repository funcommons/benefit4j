package fun.commons.benefit4j.assets.service;

import fun.commons.benefit4j.assets.entity.UbmxAccount;
import fun.commons.benefit4j.assets.entity.UbmxPosting;
import fun.commons.benefit4j.assets.mapper.UbmxAccountMapper;
import fun.commons.benefit4j.assets.mapper.UbmxPostingMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** 账户/流水查询(APP token 语境,只查本 app 数据;查询不 lazy 开户) */
@Service
@RequiredArgsConstructor
public class AssetsQueryService {

    private final UbmxAccountMapper accountMapper;
    private final UbmxPostingMapper postingMapper;

    /** 主体名下所有资产账户(§4.2);assetCode 可选过滤 */
    public List<UbmxAccount> listAccounts(Long appId, String ownerType, Long ownerId, String assetCode) {
        return accountMapper.selectList(new LambdaQueryWrapper<UbmxAccount>()
                .eq(UbmxAccount::getAppId, appId)
                .eq(UbmxAccount::getOwnerType, ownerType)
                .eq(UbmxAccount::getOwnerId, ownerId)
                .eq(StringUtils.hasText(assetCode), UbmxAccount::getAssetCode, assetCode)
                .orderByAsc(UbmxAccount::getAssetCode));
    }

    /** 账户流水分页(§4.5,src 或 dst 命中) */
    public List<UbmxPosting> listPostings(Long appId, Long accountId, int page, int size) {
        int p = Math.max(1, page);
        int s = Math.min(Math.max(1, size), 100);
        Page<UbmxPosting> pager = new Page<>(p, s);
        return postingMapper.selectPage(pager, new LambdaQueryWrapper<UbmxPosting>()
                .eq(UbmxPosting::getAppId, appId)
                .and(w -> w.eq(UbmxPosting::getSrcAccountId, accountId)
                        .or().eq(UbmxPosting::getDstAccountId, accountId))
                .orderByDesc(UbmxPosting::getCreatedAt)
                .orderByAsc(UbmxPosting::getLegSeq)).getRecords();
    }

    /** 按业务交易号查全腿(§4.5) */
    public List<UbmxPosting> listPostingsByTx(Long appId, Long txId) {
        return postingMapper.selectList(new LambdaQueryWrapper<UbmxPosting>()
                .eq(UbmxPosting::getAppId, appId)
                .eq(UbmxPosting::getTxId, txId)
                .orderByAsc(UbmxPosting::getLegSeq));
    }
}
