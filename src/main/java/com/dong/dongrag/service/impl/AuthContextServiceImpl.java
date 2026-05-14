package com.dong.dongrag.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.mapper.UserMapper;
import com.dong.dongrag.model.entity.User;
import com.dong.dongrag.service.AuthContextService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class AuthContextServiceImpl implements AuthContextService {

    @Resource
    private UserMapper userMapper;

    @Override
    public Long requireLoginUserId() {
        StpUtil.checkLogin();
        Long id = Long.valueOf(String.valueOf(StpUtil.getLoginId()));
        User row = userMapper.selectById(id);
        if (row == null) {
            StpUtil.logout();
            throw new BusinessException(
                    ErrorCode.NOT_LOGIN_ERROR,
                    "登录会话已失效：库中不存在该用户。常见于数据库重建/迁移后 Redis 仍保留旧 Token，请重新登录获取新 Token。");
        }
        return id;
    }
}
