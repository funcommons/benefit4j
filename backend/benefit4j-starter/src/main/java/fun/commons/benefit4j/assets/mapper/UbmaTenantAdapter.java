package fun.commons.benefit4j.assets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.benefit4j.entity.UbmaTenant;
import fun.commons.benefit4j.mapper.UbmaTenantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 对账 scheduler 辅助: 取全部 app(复用既有 UbmaTenantMapper) */
@Component
@RequiredArgsConstructor
public class UbmaTenantAdapter {

    private final UbmaTenantMapper tenantMapper;

    public List<Long> allTenantIds() {
        return tenantMapper.selectList(new LambdaQueryWrapper<UbmaTenant>()).stream()
                .map(UbmaTenant::getId)
                .toList();
    }
}
