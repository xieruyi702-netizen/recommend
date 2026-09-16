package com.rs.consumer.infrastructure.persistence;

import com.rs.consumer.entity.Music;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MusicMapper {

    int insert(Music music);

    Music selectByBvid(@Param("bvid") String bvid);

    Music selectById(@Param("id") long id);

    int deleteById(@Param("id") long id);

    List<Music> selectAll();
}
