package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.ProjectMemberDTO;
import com.brad.pms.entity.ProjectMemberDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final ProjectMemberMapper memberMapper;
    private final UserService userService;

    public List<ProjectMemberDTO> list(Long projectId) {
        List<ProjectMemberDO> members = memberMapper.selectList(
                new LambdaQueryWrapper<ProjectMemberDO>()
                        .eq(ProjectMemberDO::getProjectId, projectId)
                        .orderByAsc(ProjectMemberDO::getRole));
        Map<Long, UserDO> userMap = userService.listByIds(
                        members.stream().map(ProjectMemberDO::getUserId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return members.stream()
                .map(m -> Convertors.toMember(m, userMap.get(m.getUserId())))
                .collect(Collectors.toList());
    }

    public ProjectMemberDO add(Long projectId, Long userId, int role) {
        if (userId == null) {
            throw BusinessException.error("用户不能为空");
        }
        Integer exists = memberMapper.selectCount(
                new LambdaQueryWrapper<ProjectMemberDO>()
                        .eq(ProjectMemberDO::getProjectId, projectId)
                        .eq(ProjectMemberDO::getUserId, userId));
        if (exists != null && exists > 0) {
            throw BusinessException.error("该用户已在项目中");
        }
        ProjectMemberDO member = new ProjectMemberDO();
        member.setProjectId(projectId);
        member.setUserId(userId);
        member.setRole(role);
        memberMapper.insert(member);
        return member;
    }

    public void remove(Long projectId, Long memberId) {
        ProjectMemberDO member = memberMapper.selectById(memberId);
        if (member == null || !member.getProjectId().equals(projectId)) {
            throw BusinessException.error("成员不存在");
        }
        if (member.getRole() == 0) {
            throw BusinessException.error("项目负责人不可移除");
        }
        memberMapper.deleteById(memberId);
    }
}
