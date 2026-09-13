package com.rs.gateway.mapper;

import com.rs.api.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ItemMapper {

    /** 频道资讯流：category 为空时查全量，按发布时间倒序 */
    List<Item> selectNews(@Param("category") String category, @Param("limit") int limit);
}
