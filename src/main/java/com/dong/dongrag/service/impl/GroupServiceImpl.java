package com.dong.dongrag.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dong.dongrag.exception.BusinessException;
import com.dong.dongrag.exception.ErrorCode;
import com.dong.dongrag.mapper.GroupInfoMapper;
import com.dong.dongrag.mapper.GroupMembershipMapper;
import com.dong.dongrag.model.dto.group.CreateGroupRequest;
import com.dong.dongrag.model.dto.group.JoinGroupRequest;
import com.dong.dongrag.model.entity.GroupInfo;
import com.dong.dongrag.model.entity.GroupMembership;
import com.dong.dongrag.model.entity.User;
import com.dong.dongrag.model.vo.GroupVO;
import com.dong.dongrag.service.AuthContextService;
import com.dong.dongrag.service.GroupService;
import com.dong.dongrag.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupServiceImpl implements GroupService {

    @Resource
    private GroupInfoMapper groupInfoMapper;

    @Resource
    private GroupMembershipMapper groupMembershipMapper;

    @Resource
    private AuthContextService authContextService;

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGroup(CreateGroupRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || StrUtil.hasBlank(request.getGroupCode(), request.getGroupName())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        GroupInfo existed = groupInfoMapper.selectOne(new QueryWrapper<GroupInfo>()
                .eq("group_code", request.getGroupCode()).last("limit 1"));
        if (existed != null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "组编码已存在");
        }
        GroupInfo groupInfo = new GroupInfo();
        groupInfo.setGroupCode(StrUtil.trim(request.getGroupCode()));
        groupInfo.setGroupName(StrUtil.trim(request.getGroupName()));
        groupInfo.setStatus("ACTIVE");
        groupInfoMapper.insert(groupInfo);

        GroupMembership membership = new GroupMembership();
        membership.setUserId(userId);
        membership.setGroupId(groupInfo.getId());
        groupMembershipMapper.insert(membership);
        return groupInfo.getId();
    }

    @Override
    public boolean joinGroup(JoinGroupRequest request) {
        Long userId = authContextService.requireLoginUserId();
        if (request == null || request.getGroupId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        GroupInfo groupInfo = groupInfoMapper.selectById(request.getGroupId());
        if (groupInfo == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "组不存在");
        }
        Long count = groupMembershipMapper.selectCount(new QueryWrapper<GroupMembership>()
                .eq("user_id", userId).eq("group_id", request.getGroupId()));
        if (count > 0) {
            return true;
        }
        GroupMembership membership = new GroupMembership();
        membership.setUserId(userId);
        membership.setGroupId(request.getGroupId());
        return groupMembershipMapper.insert(membership) > 0;
    }

    @Override
    public List<GroupVO> listMyGroups() {
        Long userId = authContextService.requireLoginUserId();
        List<GroupMembership> memberships = groupMembershipMapper.selectList(new QueryWrapper<GroupMembership>()
                .eq("user_id", userId));
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<Long> groupIds = memberships.stream().map(GroupMembership::getGroupId).toList();
        List<GroupInfo> groups = groupInfoMapper.selectList(new QueryWrapper<GroupInfo>().in("id", groupIds));
        return groups.stream().map(this::toVO).toList();
    }

    @Override
    public void checkGroupReadable(Long userId, Long groupId) {
        if (groupId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "groupId 不能为空");
        }
        User user = userService.getById(userId);
        if (user != null && "admin".equalsIgnoreCase(user.getUserRole())) {
            return;
        }
        Long count = groupMembershipMapper.selectCount(new QueryWrapper<GroupMembership>()
                .eq("user_id", userId).eq("group_id", groupId));
        if (count <= 0) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无该组访问权限");
        }
    }

    private GroupVO toVO(GroupInfo groupInfo) {
        GroupVO vo = new GroupVO();
        vo.setId(groupInfo.getId());
        vo.setGroupCode(groupInfo.getGroupCode());
        vo.setGroupName(groupInfo.getGroupName());
        vo.setStatus(groupInfo.getStatus());
        return vo;
    }
}
