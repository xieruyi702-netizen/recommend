package com.rs.gateway.mapper;

import com.rs.api.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    int countByEmail(@Param("email") String email);

    User findByEmail(@Param("email") String email);

    User findById(@Param("id") Long id);

    /** 账号（用户名或邮箱）查用户，密码校验交给 PasswordEncoder */
    User findByAccount(@Param("account") String account);

    int updatePassword(@Param("id") long id, @Param("password") String password);

    Long findIdByUsername(@Param("username") String username);

    int insert(User user);
}
