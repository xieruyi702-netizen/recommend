package com.rs.consumer.application;

import com.rs.consumer.infrastructure.client.BiliAudioExtractor;
import com.rs.consumer.domain.Bvid;
import com.rs.consumer.domain.MusicDomainException;
import com.rs.consumer.entity.Music;
import com.rs.consumer.infrastructure.persistence.MusicMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MusicApplicationServiceTest {

    @Mock
    private BiliAudioExtractor extractor;
    @Mock
    private MusicMapper musicMapper;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MusicApplicationService service() {
        return new MusicApplicationService(extractor, musicMapper, kafkaTemplate);
    }

    private Music existing(long id) {
        Music m = Music.extractedFrom("BV1GJ411x7h7", "t", "a", 213, "", "BV1GJ411x7h7.mp3", "");
        m.setId(id);
        return m;
    }

    @Test
    void invalidUrlShouldThrowDomainException() {
        assertThrows(MusicDomainException.class, () -> service().extract("not-a-link"));
        verifyNoInteractions(musicMapper, kafkaTemplate);
    }

    @Test
    void duplicatedBvidShouldReturnExistingWithoutReextract() throws Exception {
        Music saved = existing(7L);
        when(musicMapper.selectByBvid("BV1GJ411x7h7")).thenReturn(saved);

        var outcome = service().extract("https://www.bilibili.com/video/BV1GJ411x7h7/");

        assertTrue(outcome.duplicated());
        assertEquals(7L, outcome.music().getId());
        verify(extractor, never()).extract(anyString());
        verify(musicMapper, never()).insert(any());
    }

    @Test
    void newExtractShouldInsertAndPublishDomainEvent() throws Exception {
        when(musicMapper.selectByBvid(anyString())).thenReturn(null);
        when(extractor.extract("BV1GJ411x7h7")).thenReturn(
                new BiliAudioExtractor.BiliTrack("BV1GJ411x7h7", "标题", "UP主", 213, "http://cover", "BV1GJ411x7h7.mp3", ""));

        var outcome = service().extract("https://www.bilibili.com/video/BV1GJ411x7h7/");

        assertFalse(outcome.duplicated());
        ArgumentCaptor<Music> captor = ArgumentCaptor.forClass(Music.class);
        verify(musicMapper).insert(captor.capture());
        assertEquals("BV1GJ411x7h7", captor.getValue().getBvid());
        verify(kafkaTemplate).send(eq("music-extracted"), eq("BV1GJ411x7h7"), anyString());
    }

    @Test
    void deleteShouldRemoveRecordAndFiles() throws Exception {
        when(musicMapper.selectById(7L)).thenReturn(existing(7L));
        when(extractor.resolveFile("BV1GJ411x7h7.mp3")).thenReturn(java.nio.file.Path.of("music-files/BV1GJ411x7h7.mp3"));

        service().delete(7L);

        verify(musicMapper).deleteById(7L);
        verify(extractor).resolveFile("BV1GJ411x7h7.mp3");
    }

    @Test
    void deleteUnknownIdShouldThrow() {
        when(musicMapper.selectById(99L)).thenReturn(null);
        assertThrows(MusicDomainException.class, () -> service().delete(99L));
        verify(musicMapper, never()).deleteById(anyLong());
    }
}
