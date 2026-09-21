package com.brad.pms.controller;

import com.brad.pms.ai.contract.PmsAgentContractRegistry;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.integration.dsh.api.DshAgentContractDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal, read-only contract bridge used by the DSH PMS plugin. */
@RestController
@RequestMapping("/integration/dsh/v1")
@RequiredArgsConstructor
public class DshAgentContractController {

    private final PmsAgentContractRegistry contractRegistry;

    @GetMapping("/agent-contracts/{agentId}/{contractKey}")
    public ResponseResult<DshAgentContractDTO> get(
            @PathVariable String agentId,
            @PathVariable String contractKey) {
        return ResponseResult.success(DshAgentContractDTO.from(
                contractRegistry.require(agentId, contractKey)));
    }
}
