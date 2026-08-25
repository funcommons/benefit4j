package fun.commons.benefit4j.assets.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.benefit4j.entity.UbmaApplication;
import fun.commons.benefit4j.mapper.UbmaApplicationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 对账 scheduler 辅助: 取全部 app(复用既有 UbmaApplicationMapper) */
@Component
@RequiredArgsConstructor
public class UbmaApplicationAdapter {

    private final UbmaApplicationMapper appMapper;

    public List<Long> allAppIds() {
        return appMapper.selectList(new LambdaQueryWrapper<UbmaApplication>()).stream()
                .map(UbmaApplication::getId)
                .toList();
    }
}
