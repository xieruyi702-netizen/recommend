package com.rs.recall.mapper;

import com.rs.api.entity.Item;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecallMapper {

    /** 热度召回：按热度倒序 */
    List<Item> selectByHot(@Param("limit") int limit);

    String selectInterestTags(@Param("userId") long userId);

    /** 标签召回 / ItemCF 召回：任意一个标签 LIKE 命中即可 */
    List<Item> selectByTags(@Param("tags") List<String> tags, @Param("limit") int limit);

    /** 用户最近点击的物料标签（ItemCF 种子） */
    List<String> selectClickedTags(@Param("userId") long userId, @Param("limit") int limit);

    List<Item> selectByIds(@Param("ids") List<Long> ids);
}
