package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.DevelopmentTopicUpdateCmd;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentTopicManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CreateTopicCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("title", "ownerId", "projectId", "templateVersionId");
    private static final List<String> REFRESH = List.of("topic-list", "topic-detail", "project-detail");
    private final DevelopmentTopicManagementService topicService;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.TOPIC_CREATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "topic");
        change.put("action", "create");
        change.put("title", CommandArgumentReader.requiredText(arguments, "title"));
        change.put("projectId", CommandArgumentReader.optionalLong(arguments, "projectId"));
        return support.preview(request, name(), "创建专题", change,
                List.of("专题可以不绑定项目；绑定项目时只能选择进行中的项目，并使用流程模板配置的挂载节点"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        DevelopmentTopicUpdateCmd command = new DevelopmentTopicUpdateCmd();
        command.setTitle(CommandArgumentReader.requiredText(arguments, "title"));
        command.setOwnerId(CommandArgumentReader.optionalLong(arguments, "ownerId"));
        command.setProjectId(CommandArgumentReader.optionalLong(arguments, "projectId"));
        command.setTemplateVersionId(CommandArgumentReader.optionalLong(arguments, "templateVersionId"));
        Long id = topicService.create(command);
        return support.result(operation, "专题已创建", Map.of("topicId", id), REFRESH);
    }
}
