package com.rs.rank.service;

import com.rs.api.ItemDTO;
import com.rs.api.entity.RankConfig;
import com.rs.rank.mapper.RankConfigMapper;
import com.rs.rank.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankServiceImplTest {

    @Mock
    private RankConfigMapper rankConfigMapper;
    @Mock
    private UserMapper userMapper;

    private RankServiceImpl service;

    @BeforeEach
    void setUp() {
        RankConfig config = new RankConfig();
        config.setWCtr(0.4);
        config.setWInterest(0.3);
        config.setWHot(0.2);
        config.setWFresh(0.1);
        when(rankConfigMapper.selectById(1L)).thenReturn(config);
        service = new RankServiceImpl(rankConfigMapper, userMapper);
    }

    private ItemDTO item(long id, String tags, double ctr, double hotScore, String author) {
        ItemDTO i = new ItemDTO(id, "t" + id, tags, author, hotScore, ctr,
                LocalDateTime.now().minusHours(1));
        return i;
    }

    @Test
    void rankShouldReturnEmptyForNullOrCandidates() {
        assertTrue(service.rank(1L, null, 10).isEmpty());
        assertTrue(service.rank(1L, List.of(), 10).isEmpty());
    }

    @Test
    void higherInterestMatchShouldRankFirst() {
        when(userMapper.selectInterestTags(9L)).thenReturn("科技,数码");

        ItemDTO matched = item(1, "科技,数码", 0.05, 50, "A");
        ItemDTO unmatched = item(2, "体育", 0.05, 50, "B");

        List<ItemDTO> out = service.rank(9L, List.of(unmatched, matched), 2);

        assertEquals(1, out.get(0).getId());
        assertTrue(out.get(0).getRankScore() > out.get(1).getRankScore());
    }

    @Test
    void shouldRespectRequestedSize() {
        lenient().when(userMapper.selectInterestTags(9L)).thenReturn("");
        List<ItemDTO> candidates = List.of(
                item(1, "a", 0.05, 50, "A"),
                item(2, "b", 0.05, 60, "B"),
                item(3, "c", 0.05, 70, "C"));

        assertEquals(2, service.rank(9L, candidates, 2).size());
    }

    @Test
    void userWithoutInterestsShouldStillRankByOtherSignals() {
        when(userMapper.selectInterestTags(9L)).thenReturn("");
        // lenient: 不命中兴趣的候选不会触发异常，标签差异不影响打分
        ItemDTO hot = item(1, "x", 0.10, 100, "A");
        ItemDTO cold = item(2, "x", 0.01, 10, "B");

        List<ItemDTO> out = service.rank(9L, List.of(cold, hot), 2);

        assertEquals(1, out.get(0).getId());
    }
}
