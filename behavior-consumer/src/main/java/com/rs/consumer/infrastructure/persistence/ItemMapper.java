package com.rs.consumer.infrastructure.persistence;

import com.rs.api.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ItemMapper {

    int incrementHot(@Param("delta") double delta, @Param("id") long id);

    int batchInsertIgnore(@Param("list") List<Item> items);

    Long selectIdByUrl(@Param("url") String url);

    long count();
}
