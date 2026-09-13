package com.rs.gateway.mapper;

import com.rs.api.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int countByEmail(@Param("email") String email);

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);

    /** 账号（用户名或邮箱）+ 密码登录校验 */
    User findByAccountAndPassword(@Param("account") String account, @Param("password") String password);

    Long findIdByUsername(@Param("username") String username);

    int insert(User user);
}
