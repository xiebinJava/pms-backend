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
import com.brad.pms.entity.ProjectNodeDevelopmentStoryDO;
import com.brad.pms.service.DevelopmentStoryManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UpdateStoryCommand implements PmsCommand {
    private static final Set<String> ALLOWED = CreateStoryCommand.ALLOWED;
    private static final List<String> REFRESH = CreateStoryCommand.REFRESH;
    private final DevelopmentStoryManagementService storyService;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.STORY_UPDATE; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, Set.of("storyId", "title", "topicId", "ownerId", "status", "progress",
                "storyPoints", "startDate", "dueDate", "blocker", "sort"), name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "storyId");
        ProjectNodeDevelopmentStoryDO current = storyService.requireWritableStory(id);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "story"); change.put("action", "update"); change.put("storyId", id);
        change.put("fromTopicId", current.getTopicId());
        change.put("toTopicId", topicId(arguments, current));
        change.put("title", arguments.containsKey("title")
                ? CommandArgumentReader.requiredText(arguments, "title") : current.getTitle());
        return support.preview(request, name(), "更新故事", change, List.of(), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, Set.of("storyId", "title", "topicId", "ownerId", "status", "progress",
                "storyPoints", "startDate", "dueDate", "blocker", "sort"), name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "storyId");
        ProjectNodeDevelopmentStoryDO current = storyService.requireWritableStory(id);
        DevelopmentStorySaveCmd command = new DevelopmentStorySaveCmd();
        command.setTitle(arguments.containsKey("title")
                ? CommandArgumentReader.requiredText(arguments, "title") : current.getTitle());
        command.setTopicId(topicId(arguments, current));
        command.setOwnerId(arguments.containsKey("ownerId")
                ? CommandArgumentReader.optionalLong(arguments, "ownerId") : current.getOwnerId());
        command.setStatus(arguments.containsKey("status")
                ? CommandArgumentReader.optionalText(arguments, "status") : current.getStatus());
        command.setProgress(arguments.containsKey("progress")
                ? CommandArgumentReader.optionalInteger(arguments, "progress", null) : current.getProgress());
        command.setStoryPoints(arguments.containsKey("storyPoints")
                ? CommandArgumentReader.optionalInteger(arguments, "storyPoints", null) : current.getStoryPoints());
        command.setStartDate(arguments.containsKey("startDate")
                ? CommandArgumentReader.optionalDate(arguments, "startDate") : current.getStartDate());
        command.setDueDate(arguments.containsKey("dueDate")
                ? CommandArgumentReader.optionalDate(arguments, "dueDate") : current.getDueDate());
        command.setBlocker(arguments.containsKey("blocker")
                ? CommandArgumentReader.optionalText(arguments, "blocker") : current.getBlocker());
        command.setSort(arguments.containsKey("sort")
                ? CommandArgumentReader.optionalInteger(arguments, "sort", null) : current.getSort());
        storyService.update(id, command);
        return support.result(operation, "故事已更新", Map.of("storyId", id), REFRESH);
    }

    private Long topicId(Map<String, Object> arguments, ProjectNodeDevelopmentStoryDO current) {
        return arguments.containsKey("topicId")
                ? CommandArgumentReader.optionalLong(arguments, "topicId") : current.getTopicId();
    }
}
