package com.rs.gateway.engine.operators;

import com.rs.gateway.engine.FlowContext;
import com.rs.gateway.mapper.UserMapper;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 用户画像算子：加载兴趣标签（MySQL），与召回并行执行 */
@Component
public class ProfileOperator extends AbstractRecommendOperator {

    private final UserMapper userMapper;

    public ProfileOperator(UserMapper userMapper) {
        super("profile");
        this.userMapper = userMapper;
    }

    @Override
    protected void doExecute(FlowContext ctx) {
        String tags = userMapper.selectInterestTags(ctx.getUserId());
        Set<String> interests = tags == null || tags.isBlank()
                ? Set.of() : new HashSet<>(Arrays.asList(tags.split(",")));
        ctx.set(FlowContext.PROFILE, interests);
    }
}
