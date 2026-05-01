package com.dong.dongrag.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.mapper.UserMapper;
import com.dong.dongrag.model.dto.user.UserLoginRequest;
import com.dong.dongrag.model.dto.user.UserRegisterRequest;
import com.dong.dongrag.model.entity.User;
import com.dong.dongrag.model.vo.LoginUserVO;
import com.dong.dongrag.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final String SALT = "dong-rag-user-salt";

    @Override
    public Long userRegister(UserRegisterRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userCode = StrUtil.trim(request.getUserCode());
        String displayName = StrUtil.trim(request.getDisplayName());
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        if (StrUtil.hasBlank(userCode, displayName, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (userCode.length() < 4 || userCode.length() > 32) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度需在4-32位");
        }
        if (displayName.length() > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称长度不能超过64位");
        }
        if (userPassword.length() < 6 || userPassword.length() > 32) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度需在6-32位");
        }
        if (!Objects.equals(userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码不一致");
        }
        if (getByUserCode(userCode) != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "账号已存在");
        }
        User user = new User();
        user.setUserCode(userCode);
        user.setDisplayName(displayName);
        user.setUserPassword(encryptPassword(userPassword));
        user.setUserRole("user");
        boolean saved = save(user);
        if (!saved || user.getId() == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO userLogin(UserLoginRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userCode = StrUtil.trim(request.getUserCode());
        String userPassword = request.getUserPassword();
        if (StrUtil.hasBlank(userCode, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号或密码不能为空");
        }
        User user = getByUserCode(userCode);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号不存在或密码错误");
        }
        if (!Objects.equals(user.getUserPassword(), encryptPassword(userPassword))) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "账号不存在或密码错误");
        }
        StpUtil.login(user.getId());
        LoginUserVO loginUserVO = toLoginUserVO(user);
        loginUserVO.setToken(StpUtil.getTokenValue());
        return loginUserVO;
    }

    @Override
    public boolean userLogout() {
        StpUtil.logout();
        return true;
    }

    @Override
    public List<LoginUserVO> listUsers() {
        return list().stream().map(this::toLoginUserVO).toList();
    }

    @Override
    public User getByUserCode(String userCode) {
        return getOne(new QueryWrapper<User>().eq("user_code", userCode), false);
    }

    private String encryptPassword(String password) {
        return SecureUtil.sha256(SALT + password);
    }

    private LoginUserVO toLoginUserVO(User user) {
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setUserCode(user.getUserCode());
        loginUserVO.setDisplayName(user.getDisplayName());
        loginUserVO.setUserRole(user.getUserRole());
        return loginUserVO;
    }
}
