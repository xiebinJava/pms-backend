package com.brad.pms.ai.connector.context;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.PmsCommandDescriptor;
import com.brad.pms.ai.command.PmsCommandMetadata;
import com.brad.pms.ai.command.PmsCommandRegistry;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Registry/scope-backed allowedActions for AI context responses (declarative only). */
@Component
@RequiredArgsConstructor
public class AiContextAllowedActions {

    private final PmsCommandRegistry commandRegistry;

    public List<String> projectActions() {
        return commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code))
                .filter(name -> PmsCommandMetadata.resourceTypes(name).stream()
                        .anyMatch(type -> Set.of("project", "project_node", "task").contains(type)))
                .filter(name -> PmsCommandMetadata.descriptor(name).parameters().containsKey("projectId"))
                .filter(this::scopeAvailable)
                .map(CommandName::code)
                .toList();
    }

    public List<String> developmentActions(String resourceType) {
        String identityKey = resourceType + "Id";
        return commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code))
                .filter(name -> PmsCommandMetadata.resourceTypes(name).contains(resourceType))
                .filter(name -> PmsCommandMetadata.isWorkflowComponentAction(name)
                        || PmsCommandMetadata.descriptor(name).parameters().containsKey(identityKey))
                .filter(this::scopeAvailable)
                .map(CommandName::code)
                .toList();
    }

    public List<String> componentActions(String resourceType) {
        return commandRegistry.list().stream()
                .sorted(Comparator.comparing(CommandName::code))
                .filter(PmsCommandMetadata::isWorkflowComponentAction)
                .filter(name -> PmsCommandMetadata.resourceTypes(name).contains(resourceType))
                .filter(this::scopeAvailable)
                .map(CommandName::code)
                .toList();
    }

    private boolean scopeAvailable(CommandName name) {
        PmsCommandDescriptor descriptor = PmsCommandMetadata.descriptor(name);
        return !UserContext.isDshDelegation()
                || descriptor.scopes().stream().allMatch(UserContext::hasDshScope);
    }
}
