package com.dong.dongrag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.dong.dongrag.common.BaseResponse;
import com.dong.dongrag.common.ResultUtils;
import com.dong.dongrag.model.dto.user.UserLoginRequest;
import com.dong.dongrag.model.dto.user.UserRegisterRequest;
import com.dong.dongrag.model.vo.LoginUserVO;
import com.dong.dongrag.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest request) {
        return ResultUtils.success(userService.userRegister(request));
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest request) {
        return ResultUtils.success(userService.userLogin(request));
    }

    @PostMapping("/logout")
    @SaCheckLogin
    public BaseResponse<Boolean> userLogout() {
        return ResultUtils.success(userService.userLogout());
    }

    @GetMapping("/list")
    @SaCheckRole("admin")
    public BaseResponse<List<LoginUserVO>> listUsers() {
        return ResultUtils.success(userService.listUsers());
    }
}
