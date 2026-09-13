package com.rs.consumer.mapper;

import com.rs.consumer.entity.Music;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MusicMapper {

    int insert(Music music);

    Music selectByBvid(@Param("bvid") String bvid);

    List<Music> selectAll();
}
