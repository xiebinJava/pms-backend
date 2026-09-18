package com.brad.pms.ai.command;

import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommandExecutionService {

    private final AiOperationService operationService;

    public CommandResult execute(OperationExecuteRequest request) {
        return operationService.execute(UserContext.userId(), request);
    }
}
