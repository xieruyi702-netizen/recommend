package com.rs.consumer.application.job;

import com.rs.api.entity.Item;
import com.rs.consumer.infrastructure.persistence.ItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotAggregatorTest {

    @Mock
    private ItemMapper itemMapper;
    @Mock
    private StringRedisTemplate redis;

    private Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
        HotAggregator aggregator = new HotAggregator(itemMapper, redis);
        Method m = HotAggregator.class.getDeclaredMethod(method, types);
        m.setAccessible(true);
        return m.invoke(aggregator, args);
    }

    @Test
    void unescapeShouldDecodeHtmlEntities() throws Exception {
        String out = (String) invoke("unescape", new Class<?>[]{String.class},
                "&lt;p&gt;Tom&amp;Jerry&#39;s &quot;cat&quot;&lt;/p&gt;");
        assertEquals("<p>Tom&Jerry's \"cat\"</p>", out);
    }

    @Test
    void stripTagsShouldRemoveMarkup() throws Exception {
        String out = (String) invoke("stripTags", new Class<?>[]{String.class},
                "<em class=\"keyword\">热点</em>新闻");
        assertEquals("热点新闻", out);
    }

    @Test
    void firstShouldExtractFirstMatchOrEmpty() throws Exception {
        assertEquals("abc", (String) invoke("first",
                new Class<?>[]{java.util.regex.Pattern.class, String.class},
                java.util.regex.Pattern.compile("<t>(.*?)</t>"), "<t>abc</t><t>x</t>"));
        assertEquals("", (String) invoke("first",
                new Class<?>[]{java.util.regex.Pattern.class, String.class},
                java.util.regex.Pattern.compile("<t>(.*?)</t>"), "no match"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveShouldInsertBatchAndPushNewItemsToHotRank() throws Exception {
        org.springframework.data.redis.core.ZSetOperations<String, String> zSetOps =
                org.mockito.Mockito.mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(itemMapper.batchInsertIgnore(anyList())).thenReturn(1);
        when(itemMapper.selectIdByUrl("http://x/1")).thenReturn(42L);
        when(redis.opsForZSet()).thenReturn(zSetOps);

        Item item = new Item();
        item.setTitle("标题");
        item.setTags("科技,it");
        item.setAuthor("人民网");
        item.setHotScore(55.0);
        item.setCtr(0.1);
        item.setUrl("http://x/1");
        item.setSummary("摘要");

        int inserted = (int) invoke("save", new Class<?>[]{List.class}, List.of(item));

        assertEquals(1, inserted);
        org.mockito.Mockito.verify(zSetOps).addIfAbsent("hot:rank", "42", 55.0);
    }
}
