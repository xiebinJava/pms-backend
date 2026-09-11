package com.brad.pms.dto.response;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskDetailDTO extends ProjectTaskDTO {

    private List<ProjectTaskDTO> subtasks = new ArrayList<>();
    private List<ProjectCommentDTO> comments = new ArrayList<>();
    private List<TaskAttachmentDTO> attachments = new ArrayList<>();
}
