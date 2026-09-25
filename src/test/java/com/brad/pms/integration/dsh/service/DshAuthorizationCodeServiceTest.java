package com.brad.pms.integration.dsh.service;

import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.entity.AuthSessionDO;
import com.brad.pms.entity.DshAuthorizationCodeDO;
import com.brad.pms.entity.UserDO;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeExchangeRequest;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeIssueRequest;
import com.brad.pms.integration.dsh.api.DshAuthorizationCodeIssueResponse;
import com.brad.pms.integration.dsh.security.DshAgentScopePolicy;
import com.brad.pms.mapper.AuthSessionMapper;
import com.brad.pms.mapper.DshAuthorizationCodeMapper;
import com.brad.pms.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DshAuthorizationCodeServiceTest {

    @Test
    void issueReturnsPlainCodeButPersistsOnlyItsHash() {
        DshAuthorizationCodeMapper codeMapper = mock(DshAuthorizationCodeMapper.class);
        DshAuthorizationCodeService service = new DshAuthorizationCodeService(
                codeMapper, mock(UserMapper.class), mock(AuthSessionMapper.class), new DshAgentScopePolicy());

        DshAuthorizationCodeIssueResponse response = service.issue(7L, 9L,
                new DshAuthorizationCodeIssueRequest("dsh-1", "project_assistant", Set.of("pms:project:read")));

        ArgumentCaptor<DshAuthorizationCodeDO> captor = ArgumentCaptor.forClass(DshAuthorizationCodeDO.class);
        verify(codeMapper).insert(captor.capture());
        assertThat(response.authorizationCode()).isNotBlank();
        assertThat(captor.getValue().getCodeHash()).isNotEqualTo(response.authorizationCode());
        assertThat(captor.getValue().getCodeHash()).hasSize(64);
        assertThat(captor.getValue().getUserId()).isEqualTo(7L);
        assertThat(captor.getValue().getPmsSessionId()).isEqualTo(9L);
        assertThat(response.scopes()).containsExactly("pms:project:read");
    }

    @Test
    void consumeBindsCodeToLiveUserSessionAndMarksItUsed() {
        DshAuthorizationCodeMapper codeMapper = mock(DshAuthorizationCodeMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        AuthSessionMapper sessionMapper = mock(AuthSessionMapper.class);
        DshAuthorizationCodeDO stored = storedCode();
        when(codeMapper.selectByCodeHashForUpdate(any())).thenReturn(stored);
        when(codeMapper.markUsed(eq(21L), any())).thenReturn(1);
        UserDO user = new UserDO();
        user.setId(7L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(7L)).thenReturn(user);
        AuthSessionDO session = new AuthSessionDO();
        session.setId(9L);
        session.setUserId(7L);
        session.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        when(sessionMapper.selectById(9L)).thenReturn(session);
        DshAuthorizationCodeService service = new DshAuthorizationCodeService(
                codeMapper, userMapper, sessionMapper, new DshAgentScopePolicy());

        DshAuthorizationCodeService.ConsumedAuthorizationCode consumed = service.consume(
                new DshAuthorizationCodeExchangeRequest(
                        "plain-code", "dsh-1", "project_assistant", Set.of("pms:project:read")));

        assertThat(consumed.userId()).isEqualTo(7L);
        assertThat(consumed.pmsSessionId()).isEqualTo(9L);
        assertThat(consumed.scopes()).containsExactly("pms:project:read");
        verify(codeMapper).markUsed(eq(21L), any());
    }

    @Test
    void rejectsReplayAndInvalidPmsSession() {
        DshAuthorizationCodeMapper codeMapper = mock(DshAuthorizationCodeMapper.class);
        DshAuthorizationCodeDO replayed = storedCode();
        replayed.setUsedAt(LocalDateTime.now());
        when(codeMapper.selectByCodeHashForUpdate(any())).thenReturn(replayed);
        DshAuthorizationCodeService service = new DshAuthorizationCodeService(
                codeMapper, mock(UserMapper.class), mock(AuthSessionMapper.class), new DshAgentScopePolicy());

        assertThatThrownBy(() -> service.consume(new DshAuthorizationCodeExchangeRequest(
                "plain-code", "dsh-1", "project_assistant", Set.of("pms:project:read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PMS_DSH_AUTH_CODE_INVALID");

        DshAuthorizationCodeDO liveCode = storedCode();
        when(codeMapper.selectByCodeHashForUpdate(any())).thenReturn(liveCode);
        AuthSessionMapper sessionMapper = mock(AuthSessionMapper.class);
        AuthSessionDO expired = new AuthSessionDO();
        expired.setId(9L);
        expired.setUserId(7L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(sessionMapper.selectById(9L)).thenReturn(expired);
        UserMapper userMapper = mock(UserMapper.class);
        UserDO user = new UserDO();
        user.setId(7L);
        user.setStatus("ACTIVE");
        when(userMapper.selectById(7L)).thenReturn(user);
        DshAuthorizationCodeService sessionService = new DshAuthorizationCodeService(
                codeMapper, userMapper, sessionMapper, new DshAgentScopePolicy());

        assertThatThrownBy(() -> sessionService.consume(new DshAuthorizationCodeExchangeRequest(
                "plain-code", "dsh-1", "project_assistant", Set.of("pms:project:read"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PMS_DSH_SESSION_INVALID");
    }

    private DshAuthorizationCodeDO storedCode() {
        DshAuthorizationCodeDO stored = new DshAuthorizationCodeDO();
        stored.setId(21L);
        stored.setCodeHash("9c3f2c4ab8b7f6cb0a6779f4d5e9e2a8c4a4c4c0e8a1c7b7c9b2e6a2d6e6a1f0");
        stored.setUserId(7L);
        stored.setPmsSessionId(9L);
        stored.setDshSessionId("dsh-1");
        stored.setAgentId("project_assistant");
        stored.setScopesJson("[\"pms:project:read\"]");
        stored.setExpiresAt(LocalDateTime.now().plusMinutes(1));
        return stored;
    }
}
