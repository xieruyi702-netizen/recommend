package com.rs.consumer.application;

import com.rs.api.entity.Item;
import com.rs.behavior.api.BehaviorEvent;
import com.rs.consumer.infrastructure.persistence.BehaviorMapper;
import com.rs.consumer.infrastructure.persistence.ItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BehaviorListenerTest {

    @Mock
    private BehaviorMapper behaviorMapper;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ZSetOperations<String, String> zSetOps;

    private BehaviorListener listener() {
        return new BehaviorListener(behaviorMapper, itemMapper, redis);
    }

    @Test
    void clickShouldAddScore3() {
        when(redis.opsForZSet()).thenReturn(zSetOps);

        listener().onEvent(new BehaviorEvent(1L, 2L, "click", 100L));

        verify(behaviorMapper).insertIgnore(1L, 2L, "click", "1_2_click_100");
        verify(zSetOps).incrementScore("hot:rank", "2", 3.0);
        verify(itemMapper).incrementHot(3.0, 2L);
    }

    @Test
    void likeShouldAddScore5() {
        when(redis.opsForZSet()).thenReturn(zSetOps);

        listener().onEvent(new BehaviorEvent(1L, 2L, "like", 100L));

        verify(zSetOps).incrementScore("hot:rank", "2", 5.0);
        verify(itemMapper).incrementHot(5.0, 2L);
    }

    @Test
    void exposeShouldAddScoreHalf() {
        when(redis.opsForZSet()).thenReturn(zSetOps);

        listener().onEvent(new BehaviorEvent(1L, 2L, "expose", 100L));

        verify(zSetOps).incrementScore("hot:rank", "2", 0.5);
        verify(itemMapper).incrementHot(0.5, 2L);
    }
}
