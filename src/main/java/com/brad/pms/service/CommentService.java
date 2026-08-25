package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.request.CommentCreateCmd;
import com.brad.pms.dto.response.ProjectCommentDTO;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final ProjectCommentMapper commentMapper;
    private final UserService userService;

    public List<ProjectCommentDTO> listByProject(Long projectId) {
        List<ProjectCommentDO> comments = commentMapper.selectList(
                new LambdaQueryWrapper<ProjectCommentDO>()
                        .eq(ProjectCommentDO::getProjectId, projectId)
                        .orderByDesc(ProjectCommentDO::getCreatedAt));
        Map<Long, UserDO> userMap = userService.listByIds(
                        comments.stream().map(ProjectCommentDO::getUserId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u));
        return comments.stream()
                .map(c -> Convertors.toComment(c, userMap.get(c.getUserId())))
                .collect(Collectors.toList());
    }

    public ProjectCommentDTO add(Long projectId, CommentCreateCmd cmd) {
        ProjectCommentDO comment = new ProjectCommentDO();
        comment.setProjectId(projectId);
        comment.setTaskId(cmd.getTaskId());
        comment.setContent(cmd.getContent());
        comment.setUserId(UserContext.userId());
        commentMapper.insert(comment);
        return Convertors.toComment(comment, null);
    }

    public void delete(Long id) {
        ProjectCommentDO comment = commentMapper.selectById(id);
        if (comment == null) {
            throw BusinessException.error("评论不存在");
        }
        commentMapper.deleteById(id);
    }
}
