package com.dong.dongrag.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.dong.dongrag.service.AuthContextService;
import org.springframework.stereotype.Service;

@Service
public class AuthContextServiceImpl implements AuthContextService {

    @Override
    public Long requireLoginUserId() {
        StpUtil.checkLogin();
        return Long.valueOf(String.valueOf(StpUtil.getLoginId()));
    }
}
