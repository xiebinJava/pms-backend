package com.brad.pms.ai.command;

import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommandPreviewService {

    private final PmsCommandRegistry registry;
    private final AiOperationService operationService;

    @Transactional
    public CommandPreview preview(CommandPreviewRequest request) {
        PmsCommand command = registry.require(request.name());
        CommandPreview proposal = command.preview(request);
        return operationService.persistPreview(UserContext.userId(), request, proposal);
    }
}
