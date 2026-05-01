package com.dong.dongrag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.dong.dongrag.common.BaseResponse;
import com.dong.dongrag.common.ResultUtils;
import com.dong.dongrag.model.dto.group.CreateGroupRequest;
import com.dong.dongrag.model.dto.group.JoinGroupRequest;
import com.dong.dongrag.model.vo.GroupVO;
import com.dong.dongrag.service.GroupService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/group")
@SaCheckLogin
public class GroupController {

    @Resource
    private GroupService groupService;

    @PostMapping("/create")
    public BaseResponse<Long> create(@RequestBody CreateGroupRequest request) {
        return ResultUtils.success(groupService.createGroup(request));
    }

    @PostMapping("/join")
    public BaseResponse<Boolean> join(@RequestBody JoinGroupRequest request) {
        return ResultUtils.success(groupService.joinGroup(request));
    }

    @GetMapping("/my/list")
    public BaseResponse<List<GroupVO>> myGroups() {
        return ResultUtils.success(groupService.listMyGroups());
    }
}
