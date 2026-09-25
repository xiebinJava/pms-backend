package com.brad.pms.integration.dsh.service;

import com.brad.pms.common.enums.UserStatus;
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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class DshAuthorizationCodeService {

    public static final int CODE_EXPIRES_IN_SECONDS = 90;

    private final DshAuthorizationCodeMapper codeMapper;
    private final UserMapper userMapper;
    private final AuthSessionMapper authSessionMapper;
    private final DshAgentScopePolicy scopePolicy;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public DshAuthorizationCodeService(DshAuthorizationCodeMapper codeMapper,
                                       UserMapper userMapper,
                                       AuthSessionMapper authSessionMapper,
                                       DshAgentScopePolicy scopePolicy) {
        this.codeMapper = codeMapper;
        this.userMapper = userMapper;
        this.authSessionMapper = authSessionMapper;
        this.scopePolicy = scopePolicy;
    }

    @Transactional
    public DshAuthorizationCodeIssueResponse issue(Long userId, Long pmsSessionId,
                                                    DshAuthorizationCodeIssueRequest request) {
        if (userId == null || pmsSessionId == null || request == null
                || request.dshSessionId() == null || request.dshSessionId().isBlank()
                || request.dshSessionId().length() > 128) {
            throw BusinessException.error("PMS_DSH_AUTH_CONTEXT_INVALID");
        }
        List<String> scopes = scopePolicy.resolve(request.agentId(), request.scopes());
        String plainCode = generateCode();
        LocalDateTime now = LocalDateTime.now();
        DshAuthorizationCodeDO row = new DshAuthorizationCodeDO();
        row.setCodeHash(sha256(plainCode));
        row.setUserId(userId);
        row.setPmsSessionId(pmsSessionId);
        row.setDshSessionId(request.dshSessionId().trim());
        row.setAgentId(request.agentId().trim());
        row.setScopesJson(writeScopes(scopes));
        row.setExpiresAt(now.plusSeconds(CODE_EXPIRES_IN_SECONDS));
        row.setCreatedAt(now);
        codeMapper.insert(row);
        return new DshAuthorizationCodeIssueResponse(plainCode, CODE_EXPIRES_IN_SECONDS, scopes);
    }

    @Transactional
    public ConsumedAuthorizationCode consume(DshAuthorizationCodeExchangeRequest request) {
        if (request == null || request.authorizationCode() == null || request.authorizationCode().isBlank()
                || request.dshSessionId() == null || request.dshSessionId().isBlank()) {
            throw invalidCode();
        }
        List<String> requestedScopes = scopePolicy.resolve(request.agentId(), request.scopes());
        DshAuthorizationCodeDO row = codeMapper.selectByCodeHashForUpdate(sha256(request.authorizationCode().trim()));
        LocalDateTime now = LocalDateTime.now();
        if (row == null || row.getUsedAt() != null || row.getExpiresAt() == null
                || !row.getExpiresAt().isAfter(now)) {
            throw invalidCode();
        }
        if (!Objects.equals(row.getDshSessionId(), request.dshSessionId().trim())
                || !Objects.equals(row.getAgentId(), request.agentId())) {
            throw invalidCode();
        }
        List<String> storedScopes = readScopes(row.getScopesJson());
        if (!storedScopes.equals(requestedScopes)) {
            throw invalidCode();
        }

        UserDO user = userMapper.selectById(row.getUserId());
        if (user == null || !UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw BusinessException.unauthorized("PMS_DSH_SESSION_INVALID");
        }
        AuthSessionDO session = authSessionMapper.selectById(row.getPmsSessionId());
        if (session == null || !Objects.equals(session.getUserId(), row.getUserId())
                || session.getRevokedAt() != null || session.getExpiresAt() == null
                || !session.getExpiresAt().isAfter(now)) {
            throw BusinessException.unauthorized("PMS_DSH_SESSION_INVALID");
        }
        if (codeMapper.markUsed(row.getId(), now) != 1) {
            throw invalidCode();
        }
        return new ConsumedAuthorizationCode(row.getUserId(), row.getPmsSessionId(),
                row.getDshSessionId(), row.getAgentId(), storedScopes);
    }

    private String generateCode() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String writeScopes(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (JsonProcessingException ex) {
            throw BusinessException.error("PMS_DSH_SCOPE_SERIALIZATION_FAILED");
        }
    }

    private List<String> readScopes(String scopesJson) {
        try {
            return objectMapper.readValue(scopesJson, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            throw invalidCode();
        }
    }

    private BusinessException invalidCode() {
        return BusinessException.unauthorized("PMS_DSH_AUTH_CODE_INVALID");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record ConsumedAuthorizationCode(Long userId, Long pmsSessionId,
                                             String dshSessionId, String agentId,
                                             List<String> scopes) {
    }
}
