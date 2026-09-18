package com.brad.pms.ai.context;

import com.brad.pms.dto.response.ProjectDTO;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.service.MemberService;
import com.brad.pms.service.NodeService;
import com.brad.pms.service.FollowerService;
import com.brad.pms.service.ProjectService;
import com.brad.pms.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ProjectDetailContextAssembler implements PageContextAssembler {

    private final ProjectService projectService;
    private final NodeService nodeService;
    private final MemberService memberService;
    private final FollowerService followerService;
    private final TaskService taskService;

    @Override
    public PageContextType supports() {
        return PageContextType.PROJECT_DETAIL;
    }

    @Override
    public PageContextSnapshot assemble(PageContextRequest request) {
        Long projectId = requireId(request.projectId(), "projectId");
        ProjectDTO project = projectService.detail(projectId);
        List<ProjectNodeDTO> nodes = safeList(nodeService.list(projectId));
        ProjectNodeDTO currentNode = selectCurrentNode(nodes, request.nodeId());
        Long currentNodeId = currentNode == null ? request.nodeId() : currentNode.getId();
        List<?> tasks = currentNodeId == null ? List.of() : safeList(taskService.listByProject(projectId, currentNodeId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project", project);
        data.put("nodes", nodes);
        data.put("currentNode", currentNode);
        data.put("members", safeList(memberService.list(projectId)));
        data.put("followers", safeList(followerService.list(projectId)));
        data.put("tasks", tasks);
        data.put("taskScope", currentNodeId == null ? "none" : "current-node");
        data.put("dataScope", "project-detail");
        data.put("authoritative", true);
        data.put("progress", project.getProgress());
        data.put("activeSection", request.pageState().get("activeSection"));
        data.put("projectVersion", project.getVersion());
        data.put("currentNodeVersion", currentNode == null ? null : currentNode.getVersion());
        data.put("entityVersions", entityVersions(project, currentNode));

        String nodePart = currentNodeId == null ? "none" : currentNodeId.toString();
        String nodeVersion = currentNode == null || currentNode.getVersion() == null
                ? "unknown" : currentNode.getVersion().toString();
        String projectVersion = project.getVersion() == null ? "unknown" : project.getVersion().toString();
        return ContextSnapshotFactory.create(request,
                "project-detail:" + projectId + ":" + nodePart,
                "project-" + projectVersion + ":node-" + nodeVersion,
                data);
    }

    private static ProjectNodeDTO selectCurrentNode(List<ProjectNodeDTO> nodes, Long requestedNodeId) {
        if (requestedNodeId != null) {
            return nodes.stream().filter(node -> Objects.equals(node.getId(), requestedNodeId)).findFirst().orElse(null);
        }
        return nodes.stream().filter(node -> Objects.equals(node.getStatus(), 1)).findFirst().orElse(null);
    }

    private static Map<String, Object> entityVersions(ProjectDTO project, ProjectNodeDTO node) {
        Map<String, Object> versions = new LinkedHashMap<>();
        versions.put("project", project.getVersion());
        versions.put("node", node == null ? null : node.getVersion());
        return versions;
    }

    private static Long requireId(Long value, String name) {
        if (value == null || value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
