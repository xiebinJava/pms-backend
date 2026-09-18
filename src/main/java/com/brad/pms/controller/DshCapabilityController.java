package com.brad.pms.controller;

import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshCapabilityDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/integration/dsh/v1")
public class DshCapabilityController {

    @GetMapping("/capabilities")
    public ResponseResult<DshCapabilityDTO> capabilities() {
        return ResponseResult.success(new DshCapabilityDTO(
                "v1",
                List.of(
                        "pms_project_list",
                        "pms_project_get",
                        "pms_task_list"),
                List.of(
                        "pms:project:read",
                        "pms:task:read",
                        "pms:workspace:embed"),
                List.of(
                        "project-list",
                        "project-detail",
                        "project-dashboard",
                        "workflow-template")));
    }
}
