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
import com.brad.pms.entity.ProjectNodeDevelopmentTopicDO;
import com.brad.pms.service.DevelopmentTopicManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class LinkTopicProjectCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("topicId", "projectId");
    private static final List<String> REFRESH = List.of("topic-list", "topic-detail", "project-detail");
    private final DevelopmentTopicManagementService topicService;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.TOPIC_PROJECT_LINK; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "topicId");
        ProjectNodeDevelopmentTopicDO current = topicService.requireWritableTopic(id);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "topic-project-link"); change.put("action", "replace");
        change.put("topicId", id); change.put("fromProjectId", current.getProjectId());
        change.put("toProjectId", CommandArgumentReader.optionalLong(arguments, "projectId"));
        return support.preview(request, name(), "绑定专题项目", change,
                List.of("解除项目关联时专题及其故事仍保留；绑定项目时使用专题流程模板配置的项目挂载节点"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "topicId");
        ProjectNodeDevelopmentTopicDO current = topicService.requireWritableTopic(id);
        DevelopmentTopicUpdateCmd command = new DevelopmentTopicUpdateCmd();
        command.setTitle(current.getTitle()); command.setOwnerId(current.getOwnerId());
        command.setProjectId(CommandArgumentReader.optionalLong(arguments, "projectId"));
        topicService.update(id, command);
        return support.result(operation, "专题项目关联已更新", Map.of("topicId", id), REFRESH);
    }
}
