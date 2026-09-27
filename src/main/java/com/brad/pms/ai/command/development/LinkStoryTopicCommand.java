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
public class LinkStoryTopicCommand implements PmsCommand {
    private static final Set<String> ALLOWED = Set.of("storyId", "topicId");
    private static final List<String> REFRESH = List.of("story-list", "story-detail", "topic-detail", "iteration-plan");
    private final DevelopmentStoryManagementService storyService;
    private final PmsCommandSupport support;

    @Override public CommandName name() { return CommandName.STORY_TOPIC_LINK; }

    @Override
    public CommandPreview preview(CommandPreviewRequest request) {
        Map<String, Object> arguments = request.arguments();
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "storyId");
        ProjectNodeDevelopmentStoryDO current = storyService.requireWritableStory(id);
        Map<String, Object> change = new LinkedHashMap<>();
        change.put("entity", "story-topic-link"); change.put("action", "replace");
        change.put("storyId", id); change.put("fromTopicId", current.getTopicId());
        change.put("toTopicId", CommandArgumentReader.optionalLong(arguments, "topicId"));
        return support.preview(request, name(), "绑定故事专题", change,
                List.of("解除专题关联时故事保留；重新关联后迭代计划关联会按现有领域规则重置"), REFRESH);
    }

    @Override
    public CommandResult execute(AiOperationDO operation) {
        Map<String, Object> arguments = support.readArguments(operation, name().code());
        support.rejectUnknown(arguments, ALLOWED, name().code());
        Long id = CommandArgumentReader.requiredLong(arguments, "storyId");
        ProjectNodeDevelopmentStoryDO current = storyService.requireWritableStory(id);
        DevelopmentStorySaveCmd command = new DevelopmentStorySaveCmd();
        command.setTitle(current.getTitle()); command.setOwnerId(current.getOwnerId());
        command.setStatus(current.getStatus()); command.setProgress(current.getProgress());
        command.setStoryPoints(current.getStoryPoints()); command.setStartDate(current.getStartDate());
        command.setDueDate(current.getDueDate()); command.setBlocker(current.getBlocker());
        command.setSort(current.getSort()); command.setTopicId(CommandArgumentReader.optionalLong(arguments, "topicId"));
        storyService.update(id, command);
        return support.result(operation, "故事专题关联已更新", Map.of("storyId", id), REFRESH);
    }
}
