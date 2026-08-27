package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.dto.request.MemberAddCmd;
import com.brad.pms.dto.response.ProjectMemberDTO;
import com.brad.pms.service.MemberService;
import com.brad.pms.security.PermissionCode;
import com.brad.pms.security.RequirePermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/projects/{projectId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    @RequirePermission(PermissionCode.PROJECT_READ)
    public ResponseResult<List<ProjectMemberDTO>> list(@PathVariable Long projectId) {
        return ResponseResult.success(memberService.list(projectId));
    }

    @PostMapping
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> add(@PathVariable Long projectId, @Validated @RequestBody MemberAddCmd cmd) {
        memberService.add(projectId, cmd.getUserId(), cmd.getRole());
        return ResponseResult.success();
    }

    @DeleteMapping("/{memberId}")
    @RequirePermission(PermissionCode.PROJECT_WRITE)
    public ResponseResult<Void> remove(@PathVariable Long projectId, @PathVariable Long memberId) {
        memberService.remove(projectId, memberId);
        return ResponseResult.success();
    }
}
