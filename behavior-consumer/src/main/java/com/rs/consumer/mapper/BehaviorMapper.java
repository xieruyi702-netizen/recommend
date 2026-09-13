package com.rs.consumer.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BehaviorMapper {

    int insertIgnore(@Param("userId") long userId, @Param("itemId") long itemId,
                     @Param("action") String action, @Param("eventId") String eventId);
}
