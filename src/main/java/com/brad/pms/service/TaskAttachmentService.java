package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.convertor.Convertors;
import com.brad.pms.dto.response.TaskAttachmentDTO;
import com.brad.pms.entity.ProjectTaskAttachmentDO;
import com.brad.pms.entity.ProjectTaskDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.mapper.ProjectTaskAttachmentMapper;
import com.brad.pms.mapper.ProjectTaskMapper;
import com.brad.pms.security.ProjectPermissionPolicy;
import com.brad.pms.security.UserContext;
import com.brad.pms.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskAttachmentService {

    private final ProjectTaskAttachmentMapper attachmentMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    public List<TaskAttachmentDTO> listByTask(Long taskId) {
        requireReadableTask(taskId);
        List<ProjectTaskAttachmentDO> rows = attachmentMapper.selectList(
                new LambdaQueryWrapper<ProjectTaskAttachmentDO>()
                        .eq(ProjectTaskAttachmentDO::getTaskId, taskId)
                        .orderByDesc(ProjectTaskAttachmentDO::getCreatedAt));
        return toDTOs(rows);
    }

    public TaskAttachmentDTO upload(Long taskId, MultipartFile file) {
        ProjectTaskDO task = requireWritableTask(taskId);
        FileStorageService.StoredFile stored = fileStorageService.storeAttachment(file);
        ProjectTaskAttachmentDO row = new ProjectTaskAttachmentDO();
        row.setTaskId(taskId);
        row.setProjectId(task.getProjectId());
        row.setFileKey(stored.key());
        row.setOriginalName(sanitizeName(stored.originalFilename(), stored.key()));
        row.setContentType(stored.contentType());
        row.setSizeBytes(stored.size());
        attachmentMapper.insert(row);
        return toDTO(row, userService.listByIds(List.of(UserContext.userId())).stream().findFirst().orElse(null));
    }

    public Resource loadFile(Long taskId, Long attachmentId) {
        return fileStorageService.load(requireOwnedAttachment(taskId, attachmentId).getFileKey());
    }

    public String contentType(Long taskId, Long attachmentId) {
        ProjectTaskAttachmentDO row = requireOwnedAttachment(taskId, attachmentId);
        return row.getContentType() == null ? "application/octet-stream" : row.getContentType();
    }

    public String downloadName(Long taskId, Long attachmentId) {
        return requireOwnedAttachment(taskId, attachmentId).getOriginalName();
    }

    public void delete(Long taskId, Long attachmentId) {
        ProjectTaskDO task = requireWritableTask(taskId);
        ProjectTaskAttachmentDO row = requireOwnedAttachment(taskId, attachmentId);
        Long userId = UserContext.userId();
        boolean owner = Objects.equals(row.getCreatedBy(), userId);
        boolean manager = ProjectPermissionPolicy.canManageTask(
                permissionService.requireProject(task.getProjectId()),
                permissionService.requireNode(task.getProjectId(), task.getNodeId()),
                task, userId, UserContext.isAdministrator());
        if (!owner && !manager) {
            throw BusinessException.forbidden("只能删除自己上传的附件，或由项目创建人/项目经理/节点负责人删除");
        }
        attachmentMapper.deleteById(row.getId());
        fileStorageService.delete(row.getFileKey());
    }

    public void deleteAllForTask(Long taskId) {
        List<ProjectTaskAttachmentDO> rows = attachmentMapper.selectList(
                new LambdaQueryWrapper<ProjectTaskAttachmentDO>()
                        .eq(ProjectTaskAttachmentDO::getTaskId, taskId));
        for (ProjectTaskAttachmentDO row : rows) {
            attachmentMapper.deleteById(row.getId());
            fileStorageService.delete(row.getFileKey());
        }
    }

    private ProjectTaskDO requireReadableTask(Long taskId) {
        ProjectTaskDO task = taskMapper.selectById(taskId);
        if (task == null) throw BusinessException.error("任务不存在");
        permissionService.requireProject(task.getProjectId());
        return task;
    }

    private ProjectTaskDO requireWritableTask(Long taskId) {
        ProjectTaskDO task = requireReadableTask(taskId);
        var project = permissionService.requireProject(task.getProjectId());
        var node = permissionService.requireNode(task.getProjectId(), task.getNodeId());
        Long userId = UserContext.userId();
        if (!ProjectPermissionPolicy.canManageTask(project, node, task, userId, UserContext.isAdministrator())
                && !ProjectPermissionPolicy.canEditTaskContent(project, node, task, userId, UserContext.isAdministrator())) {
            throw BusinessException.forbidden("当前用户没有上传该任务附件的权限");
        }
        return task;
    }

    private ProjectTaskAttachmentDO requireOwnedAttachment(Long taskId, Long attachmentId) {
        requireReadableTask(taskId);
        ProjectTaskAttachmentDO row = attachmentMapper.selectById(attachmentId);
        if (row == null || !Objects.equals(row.getTaskId(), taskId)) {
            throw BusinessException.error("附件不存在");
        }
        return row;
    }

    private List<TaskAttachmentDTO> toDTOs(List<ProjectTaskAttachmentDO> rows) {
        if (rows.isEmpty()) return List.of();
        Map<Long, UserDO> users = userService.listByIds(
                        rows.stream().map(ProjectTaskAttachmentDO::getCreatedBy).filter(Objects::nonNull).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(UserDO::getId, u -> u, (left, right) -> left));
        return rows.stream().map(row -> toDTO(row, users.get(row.getCreatedBy()))).collect(Collectors.toList());
    }

    private static TaskAttachmentDTO toDTO(ProjectTaskAttachmentDO row, UserDO creator) {
        TaskAttachmentDTO dto = new TaskAttachmentDTO();
        dto.setId(row.getId());
        dto.setTaskId(row.getTaskId());
        dto.setProjectId(row.getProjectId());
        dto.setOriginalName(row.getOriginalName());
        dto.setContentType(row.getContentType());
        dto.setSizeBytes(row.getSizeBytes());
        dto.setUrl("/api/tasks/" + row.getTaskId() + "/attachments/" + row.getId());
        dto.setCreatedBy(row.getCreatedBy());
        dto.setCreatedByName(Convertors.userDisplayName(creator));
        dto.setCreatedAt(row.getCreatedAt());
        return dto;
    }

    private static String sanitizeName(String original, String fallback) {
        if (original == null || original.isBlank()) return fallback;
        String name = original.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\r\\n\\t]", "").trim();
        return name.isBlank() ? fallback : name;
    }
}
