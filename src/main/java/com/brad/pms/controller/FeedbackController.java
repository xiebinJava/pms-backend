package com.brad.pms.controller;

import com.brad.pms.common.page.PageResult;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.FeedbackCreateCmd;
import com.brad.pms.dto.request.FeedbackPageQry;
import com.brad.pms.dto.request.FeedbackReopenCmd;
import com.brad.pms.dto.request.FeedbackUpdateCmd;
import com.brad.pms.dto.response.FeedbackTicketDTO;
import com.brad.pms.dto.response.FeedbackAssigneeDTO;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import com.brad.pms.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/feedback/tickets")
@RequiredArgsConstructor
@Validated
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    @RequirePermission(PermissionCode.FEEDBACK_WRITE)
    public ResponseResult<FeedbackTicketDTO> create(@Valid @RequestBody FeedbackCreateCmd cmd) {
        return ResponseResult.success(feedbackService.create(cmd));
    }

    @GetMapping
    @RequirePermission(PermissionCode.FEEDBACK_READ)
    public ResponseResult<PageResult<FeedbackTicketDTO>> list(@Valid FeedbackPageQry qry) {
        return ResponseResult.success(feedbackService.page(qry));
    }

    @GetMapping("/assignees")
    @RequirePermission(PermissionCode.FEEDBACK_MANAGE)
    public ResponseResult<List<FeedbackAssigneeDTO>> assignees() {
        return ResponseResult.success(feedbackService.assignees());
    }

    @GetMapping("/{id}")
    @RequirePermission(PermissionCode.FEEDBACK_READ)
    public ResponseResult<FeedbackTicketDTO> detail(@PathVariable Long id) {
        return ResponseResult.success(feedbackService.detail(id));
    }

    @PatchMapping("/{id}")
    @RequirePermission(PermissionCode.FEEDBACK_MANAGE)
    public ResponseResult<FeedbackTicketDTO> update(@PathVariable Long id,
                                                    @Valid @RequestBody FeedbackUpdateCmd cmd) {
        return ResponseResult.success(feedbackService.update(id, cmd));
    }

    @PostMapping("/{id}/reopen")
    @RequirePermission(PermissionCode.FEEDBACK_WRITE)
    public ResponseResult<FeedbackTicketDTO> reopen(@PathVariable Long id,
                                                    @Valid @RequestBody FeedbackReopenCmd cmd) {
        return ResponseResult.success(feedbackService.reopen(id, cmd));
    }
}
