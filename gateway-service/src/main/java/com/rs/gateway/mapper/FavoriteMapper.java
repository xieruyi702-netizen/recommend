package com.rs.gateway.mapper;

import com.rs.api.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FavoriteMapper {

    int insertIgnore(@Param("userId") long userId, @Param("itemId") long itemId);

    int delete(@Param("userId") long userId, @Param("itemId") long itemId);

    List<Item> selectByUserId(@Param("userId") long userId);
}
