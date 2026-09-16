package com.rs.rank.mapper;

import com.rs.api.entity.RankConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RankConfigMapper {

    RankConfig selectById(Long id);
}
