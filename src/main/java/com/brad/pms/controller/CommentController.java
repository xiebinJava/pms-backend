package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.CommentCreateCmd;
import com.brad.pms.dto.response.ProjectCommentDTO;
import com.brad.pms.service.CommentService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/projects/{projectId}/comments")
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<ProjectCommentDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(commentService.listByProject(projectId));
    }

    @PostMapping("/projects/{projectId}/comments")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<ProjectCommentDTO> add(@PathVariable Long projectId,
                                                 @Validated @RequestBody CommentCreateCmd cmd) {
        return ResponseResult.success(commentService.add(projectId, cmd));
    }

    @DeleteMapping("/comments/{id}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> delete(@PathVariable Long id) {
        commentService.delete(id);
        return ResponseResult.success();
    }
}
