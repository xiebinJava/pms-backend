package com.brad.pms.controller;

import com.brad.pms.ai.command.CommandExecutionService;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandPreviewService;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.ai.api.AiOperationResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiCommandController {

    private final CommandPreviewService previewService;
    private final CommandExecutionService executionService;
    private final PmsCommandRegistry registry;

    @GetMapping("/commands")
    public ResponseResult<List<String>> commands() {
        return ResponseResult.success(registry.list().stream().map(CommandName::code).sorted().toList());
    }

    @PostMapping("/commands/preview")
    public ResponseResult<CommandPreview> preview(@RequestBody CommandPreviewRequest request) {
        return ResponseResult.success(previewService.preview(request));
    }

    @PostMapping("/operations/{operationId}/execute")
    public ResponseResult<AiOperationResultDTO> execute(@PathVariable String operationId,
                                                       @RequestBody ExecuteBody body) {
        CommandResult result = executionService.execute(
                new OperationExecuteRequest(operationId, body.idempotencyKey()));
        return ResponseResult.success(AiOperationResultDTO.from(result));
    }

    public record ExecuteBody(String idempotencyKey) {
    }
}
