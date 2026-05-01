package com.dong.dongrag.service;

import com.dong.dongrag.model.dto.group.CreateGroupRequest;
import com.dong.dongrag.model.dto.group.JoinGroupRequest;
import com.dong.dongrag.model.vo.GroupVO;

import java.util.List;

public interface GroupService {

    Long createGroup(CreateGroupRequest request);

    boolean joinGroup(JoinGroupRequest request);

    List<GroupVO> listMyGroups();

    void checkGroupReadable(Long userId, Long groupId);
}
