package com.brad.pms.ai;

import com.brad.pms.ai.command.CommandName;
import com.brad.pms.ai.command.CommandPreviewRequest;
import com.brad.pms.ai.command.OperationExecuteRequest;
import com.brad.pms.ai.context.PageContextRequest;
import com.brad.pms.ai.context.PageContextType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCommandContractTest {

    @Test
    void pageContextTypesExposeStableWireNames() {
        assertThat(PageContextType.PROJECT_LIST.code()).isEqualTo("project-list");
        assertThat(PageContextType.PROJECT_DASHBOARD.code()).isEqualTo("project-dashboard");
        assertThat(PageContextType.PROJECT_DETAIL.code()).isEqualTo("project-detail");
        assertThat(PageContextType.WORKFLOW_TEMPLATE.code()).isEqualTo("workflow-template");
    }

    @Test
    void commandNamesExposeStableWireNames() {
        assertThat(CommandName.TASK_CREATE.code()).isEqualTo("task.create");
        assertThat(CommandName.TASK_ASSIGN.code()).isEqualTo("task.assign");
    }

    @Test
    void previewRequestDoesNotAcceptAnExecutionIdempotencyKey() {
        CommandPreviewRequest request = new CommandPreviewRequest(
                CommandName.TASK_CREATE,
                Map.of("title", "接口测试"),
                "ctx-1",
                "v-1");

        assertThat(request.name()).isEqualTo(CommandName.TASK_CREATE);
        assertThat(request.arguments()).containsEntry("title", "接口测试");
        assertThat(request.contextId()).isEqualTo("ctx-1");
        assertThat(request.contextVersion()).isEqualTo("v-1");
    }

    @Test
    void operationExecutionRequiresOperationIdAndIdempotencyKey() {
        assertThatThrownBy(() -> new OperationExecuteRequest("", "key-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("operationId");
        assertThatThrownBy(() -> new OperationExecuteRequest("op-1", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("idempotencyKey");

        OperationExecuteRequest request = new OperationExecuteRequest("op-1", "key-1");
        assertThat(request.operationId()).isEqualTo("op-1");
        assertThat(request.idempotencyKey()).isEqualTo("key-1");
    }

    @Test
    void pageContextRequestCopiesUntrustedPageState() {
        Map<String, Object> state = new java.util.HashMap<>();
        state.put("search", "接口");

        PageContextRequest request = new PageContextRequest(
                PageContextType.PROJECT_LIST,
                "/projects",
                null,
                null,
                state);
        state.put("search", "被修改");

        assertThat(request.pageState()).containsEntry("search", "接口");
        assertThatThrownBy(() -> request.pageState().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
