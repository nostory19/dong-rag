package com.dong.dongrag.config;

import cn.dev33.satoken.stp.StpInterface;
import com.dong.dongrag.model.entity.User;
import com.dong.dongrag.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class SaTokenPermissionImpl implements StpInterface {

    @Resource
    private UserService userService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Long userId = Long.valueOf(String.valueOf(loginId));
        User user = userService.getById(userId);
        if (user == null || user.getUserRole() == null) {
            return Collections.emptyList();
        }
        return List.of(user.getUserRole());
    }
}
