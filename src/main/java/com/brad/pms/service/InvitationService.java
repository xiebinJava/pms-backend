package com.brad.pms.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.common.exception.BusinessException;
import com.brad.pms.config.EnterpriseDataMigration;
import com.brad.pms.dto.request.ActivationRequest;
import com.brad.pms.dto.request.UserInviteCmd;
import com.brad.pms.dto.response.InvitationResponse;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import com.brad.pms.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InvitationService {
    private final UserMapper userMapper;
    private final InvitationMapper invitationMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserPositionMapper userPositionMapper;
    private final AuthService authService;
    private final OperationLogService operationLogService;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Transactional
    public InvitationResponse invite(UserInviteCmd cmd) {
        String normalized = EnterpriseDataMigration.normalizeUsername(cmd.getUsername());
        if (normalized == null || !normalized.matches("^[a-z][a-z0-9._-]{1,49}$")) {
            throw BusinessException.error("英文名需以字母开头，只能包含字母、数字、点、下划线或短横线");
        }
        if (userMapper.findByUsernameNormalized(normalized) != null) throw BusinessException.error("英文名已存在");
        OrgUnitDO primaryOrg = requireOrg(cmd.getOrgUnitId());
        UserDO user = new UserDO();
        user.setUsername(cmd.getUsername().trim());
        user.setUsernameNormalized(normalized);
        user.setNameZh(cmd.getNameZh().trim());
        user.setNickname(cmd.getNameZh().trim());
        user.setEmail(cmd.getEmail());
        user.setPhone(cmd.getPhone());
        user.setPassword(encoder.encode(randomToken()));
        user.setStatus(UserStatus.PENDING_ACTIVATION.name());
        user.setFailedLoginCount(0);
        userMapper.insert(user);

        UserPositionDO position = new UserPositionDO();
        position.setUserId(user.getId());
        position.setOrgUnitId(primaryOrg.getId());
        position.setPositionId(cmd.getPositionId());
        position.setAssignmentType("PRIMARY");
        position.setIsPrimary(true);
        position.setStartDate(java.time.LocalDate.now());
        position.setStatus("ACTIVE");
        userPositionMapper.insert(position);
        if (cmd.getRoleCode() != null && !cmd.getRoleCode().isBlank()) {
            RoleDO role = roleMapper.findByCode(cmd.getRoleCode());
            if (role == null) throw BusinessException.error("角色不存在");
            UserRoleDO grant = new UserRoleDO();
            grant.setUserId(user.getId());
            grant.setRoleId(role.getId());
            grant.setStartAt(LocalDateTime.now());
            grant.setStatus("ACTIVE");
            userRoleMapper.insert(grant);
        }
        String raw = randomToken();
        InvitationDO invitation = new InvitationDO();
        invitation.setUserId(user.getId());
        invitation.setTokenHash(AuthService.sha256(raw));
        invitation.setExpiresAt(LocalDateTime.now().plusDays(3));
        invitation.setStatus("PENDING");
        invitation.setCreatedBy(UserContext.userId());
        invitationMapper.insert(invitation);
        operationLogService.record("USER_INVITED", "USER", user.getId(), null, java.util.Map.of("username", user.getUsername(), "nameZh", user.getNameZh()));
        return new InvitationResponse(user.getId(), "/auth/activate?token=" + raw, invitation.getExpiresAt().toString());
    }

    @Transactional
    public void activate(ActivationRequest request) {
        InvitationDO invitation = invitationMapper.findPendingByHash(AuthService.sha256(request.getToken()));
        if (invitation == null || invitation.getExpiresAt().isBefore(LocalDateTime.now())) throw BusinessException.error("邀请链接已失效");
        UserDO user = userMapper.selectById(invitation.getUserId());
        if (user == null || !UserStatus.PENDING_ACTIVATION.name().equals(user.getStatus())) throw BusinessException.error("账号已激活或不可用");
        user.setPassword(encoder.encode(request.getPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setStatus(UserStatus.ACTIVE.name());
        userMapper.updateById(user);
        invitationMapper.markUsed(invitation.getId());
        authService.revokeAllSessions(user.getId(), "USER_ACTIVATED");
        operationLogService.record("USER_ACTIVATED", "USER", user.getId(), java.util.Map.of("status", "PENDING_ACTIVATION"), java.util.Map.of("status", "ACTIVE"));
    }

    private OrgUnitDO requireOrg(Long id) {
        OrgUnitDO org = orgUnitMapper.selectById(id);
        if (org == null || !"ACTIVE".equals(org.getStatus())) throw BusinessException.error("组织不存在或已停用");
        return org;
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
