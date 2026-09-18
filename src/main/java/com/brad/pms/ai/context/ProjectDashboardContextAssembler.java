package com.brad.pms.ai.context;

import com.brad.pms.dto.response.ProjectBoardDTO;
import com.brad.pms.service.ProjectBoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ProjectDashboardContextAssembler implements PageContextAssembler {

    private static final int MAX_BOARD_ROWS = 50;
    private final ProjectBoardService projectBoardService;

    @Override
    public PageContextType supports() {
        return PageContextType.PROJECT_DASHBOARD;
    }

    @Override
    public PageContextSnapshot assemble(PageContextRequest request) {
        Long orgUnitId = longValue(request.pageState().get("orgUnitId"));
        ProjectBoardDTO board = projectBoardService.getBoard(orgUnitId);
        ProjectBoardDTO capped = board == null ? null : new ProjectBoardDTO(
                board.asOfDate(), board.generatedAt(), board.allCompanyScope(),
                board.projects() == null ? List.of() : board.projects().stream().limit(MAX_BOARD_ROWS).toList());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("board", capped);
        data.put("orgUnitId", orgUnitId);
        return ContextSnapshotFactory.create(request, "project-dashboard", boardVersion(capped), data);
    }

    private static String boardVersion(ProjectBoardDTO board) {
        return board == null || board.generatedAt() == null ? "empty" : board.generatedAt();
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String string) {
            try { return Long.valueOf(string); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
}
