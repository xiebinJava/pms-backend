package com.brad.pms.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.dto.request.CommentCreateCmd;
import com.brad.pms.entity.ProjectCommentDO;
import com.brad.pms.entity.ProjectDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectCommentMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock ProjectCommentMapper commentMapper;
    @Mock ProjectTaskMapper taskMapper;
    @Mock UserService userService;
    @Mock ProjectPermissionService permissionService;
    @Mock NotificationService notificationService;
    @Mock OperationLogService operationLogService;

    @InjectMocks CommentService commentService;

    @BeforeEach
    void init() {
        Configuration configuration = new Configuration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "test");
        TableInfoHelper.initTableInfo(assistant, ProjectCommentDO.class);
        UserContext.set(new LoginUser(7L, "Alex.Zhang", "张伟"));
    }

    @AfterEach
    void clear() {
        UserContext.clear();
    }

    @Test
    void addRejectsTaskFromAnotherProject() {
        when(permissionService.requireProjectCommentWritable(9L)).thenReturn(new ProjectDO());
        ProjectTaskDO foreign = new ProjectTaskDO();
        foreign.setId(4L);
        foreign.setProjectId(88L);
        when(taskMapper.selectById(4L)).thenReturn(foreign);

        CommentCreateCmd cmd = new CommentCreateCmd();
        cmd.setContent("对齐接口");
        cmd.setTaskId(4L);

        assertThatThrownBy(() -> commentService.add(9L, cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于当前项目");
    }

    @Test
    void addFillsAuthorDisplayName() {
        when(permissionService.requireProjectCommentWritable(9L)).thenReturn(new ProjectDO());
        ProjectTaskDO task = new ProjectTaskDO();
        task.setId(4L);
        task.setProjectId(9L);
        when(taskMapper.selectById(4L)).thenReturn(task);
        UserDO author = new UserDO();
        author.setId(7L);
        author.setUsername("Alex.Zhang");
        author.setNameZh("张伟");
        when(userService.listByIds(any())).thenReturn(List.of(author));

        CommentCreateCmd cmd = new CommentCreateCmd();
        cmd.setContent("已收到");
        cmd.setTaskId(4L);

        var dto = commentService.add(9L, cmd);

        verify(commentMapper).insert(any(ProjectCommentDO.class));
        assertThat(dto.getUserNickname()).isEqualTo("张伟（Alex.Zhang）");
        assertThat(dto.getContent()).isEqualTo("已收到");
    }
}
