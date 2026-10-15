package com.brad.pms.controller;

import com.brad.pms.ai.orchestrator.api.AiOrchestratorRequest;
import com.brad.pms.ai.orchestrator.api.AiOrchestratorResponse;
import com.brad.pms.ai.orchestrator.service.AiOrchestratorService;
import com.brad.pms.common.response.ResponseResult;
import com.brad.pms.security.LoginUser;
import com.brad.pms.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiOrchestratorControllerTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void createsConversationThroughTheAuthenticatedFirstPartyClient() {
        UserContext.set(new LoginUser(7L, "alex", "Alex"));
        AiOrchestratorService service = mock(AiOrchestratorService.class);
        AiOrchestratorResponse.ConversationView view = new AiOrchestratorResponse.ConversationView(
                10L, "requirement", 33L, "ACTIVE", null);
        when(service.createConversation(eq(7L), any())).thenReturn(view);
        AiOrchestratorController controller = new AiOrchestratorController(service);

        ResponseResult<AiOrchestratorResponse.ConversationView> response = controller.createConversation(
                "pms-cli", new AiOrchestratorRequest.CreateConversationRequest("requirement", 33L));

        assertThat(response.getData()).isEqualTo(view);
        verify(service).createConversation(eq(7L), any(AiOrchestratorRequest.CreateConversationCommand.class));
    }

    @Test
    void rejectsUnsupportedConnectorClientBeforeCallingTheService() {
        UserContext.set(new LoginUser(7L, "alex", "Alex"));
        AiOrchestratorService service = mock(AiOrchestratorService.class);
        AiOrchestratorController controller = new AiOrchestratorController(service);

        assertThatThrownBy(() -> controller.createConversation(
                "unknown-client", new AiOrchestratorRequest.CreateConversationRequest("requirement", 33L)))
                .hasMessageContaining("连接器客户端不受支持");
    }

    @Test
    void routesEventsAndPreservesTheAfterSequenceCursor() {
        UserContext.set(new LoginUser(7L, "alex", "Alex"));
        AiOrchestratorService service = mock(AiOrchestratorService.class);
        List<AiOrchestratorResponse.OperationEventView> events = List.of(
                new AiOrchestratorResponse.OperationEventView(2L, "PLAN_CREATED", null, java.util.Map.of(), null));
        when(service.listEvents(7L, 10L, 1L)).thenReturn(events);
        AiOrchestratorController controller = new AiOrchestratorController(service);

        ResponseResult<List<AiOrchestratorResponse.OperationEventView>> response = controller.events(
                "pms-cli", 10L, 1L);

        assertThat(response.getData()).containsExactlyElementsOf(events);
        verify(service).listEvents(7L, 10L, 1L);
    }

    @Test
    void exposesTheDocumentedEventsRouteWithClientScope() throws Exception {
        UserContext.set(new LoginUser(7L, "alex", "Alex"));
        AiOrchestratorService service = mock(AiOrchestratorService.class);
        when(service.listEvents(7L, 10L, 0L)).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiOrchestratorController(service)).build();

        mvc.perform(get("/integration/ai/v1/orchestrator/conversations/10/events")
                        .header("x-client-id", "pms-cli"))
                .andExpect(status().isOk());
    }

    @Test
    void exposesTheAtomicSessionRouteWithClientScope() throws Exception {
        UserContext.set(new LoginUser(7L, "alex", "Alex"));
        AiOrchestratorService service = mock(AiOrchestratorService.class);
        AiOrchestratorResponse.SessionView session = new AiOrchestratorResponse.SessionView(
                10L,
                new AiOrchestratorResponse.ConversationView(10L, "requirement", 33L, "ACTIVE", null),
                null,
                List.of(),
                4L,
                5L);
        when(service.getSession(7L, 10L, 4L)).thenReturn(session);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AiOrchestratorController(service)).build();

        mvc.perform(get("/integration/ai/v1/orchestrator/conversations/10/session")
                        .queryParam("afterSequence", "4")
                        .header("x-client-id", "pms-cli"))
                .andExpect(status().isOk());

        verify(service).getSession(7L, 10L, 4L);
    }
}
