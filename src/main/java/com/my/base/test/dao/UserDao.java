package com.my.base.test.dao;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.my.base.test.domain.User;
import com.my.base.test.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * @author
 * @date 2025/2/19 9:36
 * @description:
 */
@Service
public class UserDao extends ServiceImpl<UserMapper, User> {

}
