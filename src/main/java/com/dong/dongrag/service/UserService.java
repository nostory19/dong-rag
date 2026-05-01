package com.dong.dongrag.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dong.dongrag.model.dto.user.UserLoginRequest;
import com.dong.dongrag.model.dto.user.UserRegisterRequest;
import com.dong.dongrag.model.entity.User;
import com.dong.dongrag.model.vo.LoginUserVO;

import java.util.List;

public interface UserService extends IService<User> {

    Long userRegister(UserRegisterRequest request);

    LoginUserVO userLogin(UserLoginRequest request);

    boolean userLogout();

    List<LoginUserVO> listUsers();

    User getByUserCode(String userCode);
}
