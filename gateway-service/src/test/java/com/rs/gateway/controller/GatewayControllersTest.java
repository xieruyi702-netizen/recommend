package com.rs.gateway.controller;

import com.rs.api.ItemDTO;
import com.rs.api.entity.Item;
import com.rs.behavior.api.BehaviorEvent;
import com.rs.gateway.auth.TokenService;
import com.rs.gateway.mapper.FavoriteMapper;
import com.rs.gateway.mapper.ItemMapper;
import com.rs.gateway.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayControllersTest {

    @Mock
    private ItemMapper itemMapper;
    @Mock
    private FavoriteMapper favoriteMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private KafkaTemplate<String, BehaviorEvent> kafkaTemplate;
    @Mock
    private TokenService tokenService;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Item entity(long id) {
        Item i = new Item();
        i.setId(id);
        i.setTitle("t" + id);
        i.setTags("科技");
        i.setAuthor("A");
        i.setHotScore(50.0);
        i.setCtr(0.05);
        i.setPublishTime(LocalDateTime.now());
        return i;
    }

    @Test
    void newsListShouldClampSizeTo50() {
        when(itemMapper.selectNews(eq("科技"), anyInt())).thenReturn(List.of());

        new NewsController(itemMapper).list("科技", 100);

        verify(itemMapper).selectNews("科技", 50);
    }

    @Test
    void newsListShouldMapEntityToDTO() {
        when(itemMapper.selectNews("", 20)).thenReturn(List.of(entity(3)));

        List<ItemDTO> out = new NewsController(itemMapper).list("", 20);

        assertEquals(1, out.size());
        assertEquals(3, out.get(0).getId());
        assertEquals("t3", out.get(0).getTitle());
    }

    @Test
    void favoriteToggleShouldInsertOrDelete() {
        Map<String, Object> body = Map.of("userId", 1L, "itemId", 2L, "liked", true);
        Map<String, Object> out = new FavoriteController(favoriteMapper).toggle(body);

        verify(favoriteMapper).insertIgnore(1L, 2L);
        assertEquals(Boolean.TRUE, out.get("liked"));

        new FavoriteController(favoriteMapper).toggle(Map.of("userId", 1L, "itemId", 2L, "liked", false));
        verify(favoriteMapper).delete(1L, 2L);
    }

    @Test
    void favoriteListShouldMapToFavorites() {
        when(favoriteMapper.selectByUserId(1L)).thenReturn(List.of(entity(5)));

        var out = new FavoriteController(favoriteMapper).list(1L);

        assertEquals(1, out.size());
        assertEquals(5, out.get(0).getId());
    }

    @Test
    void loginShouldFailOnWrongAccount() {
        when(userMapper.findByAccount("alice")).thenReturn(null);

        Map<String, Object> out = new UserController(userMapper, tokenService, passwordEncoder)
                .login(Map.of("account", "alice", "password", "bad"));

        assertEquals(false, out.get("ok"));
    }

    @Test
    void loginShouldReturnUserInfoOnSuccess() {
        com.rs.api.entity.User u = new com.rs.api.entity.User();
        u.setId(7L);
        u.setUsername("alice");
        u.setInterestTags("科技");
        u.setPassword("{bcrypt}$2a$10$storedhash");
        when(userMapper.findByAccount("alice")).thenReturn(u);
        when(passwordEncoder.matches("pw", "$2a$10$storedhash")).thenReturn(true);
        when(tokenService.issue(7L)).thenReturn("tok-7");

        Map<String, Object> out = new UserController(userMapper, tokenService, passwordEncoder)
                .login(Map.of("account", "alice", "password", "pw"));

        assertEquals(true, out.get("ok"));
        assertEquals(7L, out.get("userId"));
        assertEquals("科技", out.get("interestTags"));
    }

    @Test
    void registerShouldRejectInvalidEmail() {
        Map<String, Object> out = new UserController(userMapper, tokenService, passwordEncoder)
                .register(Map.of("username", "a", "email", "not-an-email", "password", "p"));

        assertEquals(false, out.get("ok"));
        verify(userMapper, never()).insert(any());
    }

    @Test
    void behaviorReportShouldDefaultTimestampAndSendToKafka() {
        BehaviorEvent event = new BehaviorEvent(1L, 2L, "click", 0);

        Map<String, Object> out = new BehaviorController(kafkaTemplate).report(event);

        assertEquals(true, out.get("ok"));
        assertNotEquals(0, event.getTimestamp());
        ArgumentCaptor<BehaviorEvent> captor = ArgumentCaptor.forClass(BehaviorEvent.class);
        verify(kafkaTemplate).send(eq("user-behavior"), eq("1"), captor.capture());
        assertEquals("click", captor.getValue().getAction());
    }
}
