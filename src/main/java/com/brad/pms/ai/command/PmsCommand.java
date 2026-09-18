package com.brad.pms.ai.command;

import com.brad.pms.entity.AiOperationDO;

/** A typed, allow-listed PMS capability. */
public interface PmsCommand {

    CommandName name();

    CommandPreview preview(CommandPreviewRequest request);

    CommandResult execute(AiOperationDO operation);
}
