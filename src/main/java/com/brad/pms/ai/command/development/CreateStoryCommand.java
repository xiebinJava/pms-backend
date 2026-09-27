package com.brad.pms.ai.command.development;

import com.brad.pms.ai.command.CommandArgumentReader;
import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreview;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.CommandResult;
import com.brad.pms.ai.command.PmsCommand;
import com.brad.pms.ai.command.PmsCommandSupport;
import com.brad.pms.dto.request.DevelopmentStorySaveCmd;
import com.brad.pms.entity.AiOperationDO;
import com.brad.pms.service.DevelopmentStoryManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class CreateStoryCommand implements PmsCommand {
    protected static final Set<String> ALLOWED = Set.of("title", "topicId", "ownerId", "templateVersionId",
            "status", "progress", "storyPoints", "startDate", "dueDate", "blocker", "sort");
    protected static final List<String> REFRESH = List.of("story-list", "story-detail", "topic-detail", "iteration-plan");
    protected final DevelopmentStoryManagementService storyService;
    protected final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.STORY_CREATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "story"); change.put("action", "create");
        change.put("title", CommandArgumentReader.requiredText(arguments, "title"));
        change.put("topicId", CommandArgumentReader.optionalLong(arguments, "topicId"));
        return support.preview(request, name(), "创建故事", change,
                List.of("故事可以不关联专题；关联专题时使用专题流程模板配置的故事挂载节点"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = storyService.create(toCommand(arguments, true));
        return support.result(operation, "故事已创建", Map.of("storyId", id), REFRESH);
    }

    protected DevelopmentStorySaveCmd toCommand(Map<String, Object> arguments, boolean create) {
        DevelopmentStorySaveCmd command = new DevelopmentStorySaveCmd();
        command.setTitle(CommandArgumentReader.requiredText(arguments, "title"));
        command.setTopicId(CommandArgumentReader.optionalLong(arguments, "topicId"));
        command.setOwnerId(CommandArgumentReader.optionalLong(arguments, "ownerId"));
        command.setTemplateVersionId(CommandArgumentReader.optionalLong(arguments, "templateVersionId"));
        command.setStatus(CommandArgumentReader.optionalText(arguments, "status"));
        command.setProgress(CommandArgumentReader.optionalInteger(arguments, "progress", null));
        command.setStoryPoints(CommandArgumentReader.optionalInteger(arguments, "storyPoints", null));
        command.setStartDate(CommandArgumentReader.optionalDate(arguments, "startDate"));
        command.setDueDate(CommandArgumentReader.optionalDate(arguments, "dueDate"));
        command.setBlocker(CommandArgumentReader.optionalText(arguments, "blocker"));
        command.setSort(CommandArgumentReader.optionalInteger(arguments, "sort", null));
        return command;
    }
}
