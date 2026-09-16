package com.rs.rank.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    String selectInterestTags(@Param("userId") long userId);
}
