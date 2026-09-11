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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;

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
    private final ObjectProvider<InvitationNotifier> notifierProvider;
    private final SecureRandom random = new SecureRandom();
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${pms.auth.invitation-expose-token:false}")
    private boolean exposeToken;

    @Transactional
    public InvitationResponse invite(UserInviteCmd cmd) {
        String email = cmd.getEmail() == null ? null : cmd.getEmail().trim();
        String emailNormalized = EnterpriseDataMigration.normalizeEmail(email);
        if (emailNormalized == null || emailNormalized.isBlank() || !emailNormalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw BusinessException.error("请输入有效邮箱");
        }
        if (userMapper.findByEmailNormalized(emailNormalized) != null) throw BusinessException.error("邮箱已存在");
        String username = cmd.getUsername() == null ? null : cmd.getUsername().trim();
        String usernameNormalized = EnterpriseDataMigration.normalizeUsername(username);
        if (usernameNormalized != null && !usernameNormalized.isBlank()
                && !usernameNormalized.matches("^[a-z][a-z0-9._-]{1,49}$")) {
            throw BusinessException.error("英文名需以字母开头，只能包含字母、数字、点、下划线或短横线");
        }
        if (usernameNormalized != null && userMapper.findByUsernameNormalized(usernameNormalized) != null) {
            throw BusinessException.error("英文名已存在");
        }
        OrgUnitDO primaryOrg = requireOrg(cmd.getOrgUnitId());
        RoleDO grantedRole = resolveInviteRole(cmd.getRoleCode());
        UserDO user = new UserDO();
        user.setUsername(username == null || username.isBlank() ? generatedLegacyUsername(emailNormalized) : username);
        user.setUsernameNormalized(usernameNormalized == null || usernameNormalized.isBlank()
                ? EnterpriseDataMigration.normalizeUsername(user.getUsername()) : usernameNormalized);
        String nameZh = cmd.getNameZh() == null ? null : cmd.getNameZh().trim();
        user.setNameZh(nameZh == null || nameZh.isBlank() ? null : nameZh);
        user.setNickname(user.getNameZh());
        user.setEmail(email);
        user.setEmailNormalized(emailNormalized);
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
        if (grantedRole != null) {
            UserRoleDO grant = new UserRoleDO();
            grant.setUserId(user.getId());
            grant.setRoleId(grantedRole.getId());
            grant.setStartAt(LocalDateTime.now());
            grant.setStatus("ACTIVE");
            userRoleMapper.insert(grant);
        }
        InvitationResponse response = issueActivation(user);
        java.util.Map<String, Object> after = new java.util.LinkedHashMap<>();
        after.put("email", user.getEmail());
        if (user.getNameZh() != null) after.put("nameZh", user.getNameZh());
        if (cmd.getUsername() != null && !cmd.getUsername().isBlank()) after.put("username", user.getUsername());
        operationLogService.record("USER_INVITED", "USER", user.getId(), null, after);
        return response;
    }

    @Transactional
    public InvitationResponse reinvite(Long userId) {
        UserDO user = userMapper.selectById(userId);
        if (user == null) throw BusinessException.notFound("账号不存在");
        if (!UserStatus.PENDING_ACTIVATION.name().equals(user.getStatus())) {
            throw BusinessException.error("只有待激活账号可以重新发送激活链接");
        }
        invitationMapper.expirePendingByUserId(user.getId());
        InvitationResponse response = issueActivation(user);
        operationLogService.record("USER_REINVITED", "USER", user.getId(),
                java.util.Map.of("status", UserStatus.PENDING_ACTIVATION.name()),
                java.util.Map.of("email", user.getEmail()));
        return response;
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

    private InvitationResponse issueActivation(UserDO user) {
        String raw = randomToken();
        InvitationDO invitation = new InvitationDO();
        invitation.setUserId(user.getId());
        invitation.setTokenHash(AuthService.sha256(raw));
        invitation.setExpiresAt(LocalDateTime.now().plusDays(3));
        invitation.setStatus("PENDING");
        invitation.setCreatedBy(UserContext.userId() == null ? user.getId() : UserContext.userId());
        invitationMapper.insert(invitation);
        String activationUrl = "/auth/activate?token=" + raw;
        InvitationNotifier notifier = notifierProvider.getIfAvailable();
        if (notifier != null) {
            notifier.send(user, activationUrl, invitation.getExpiresAt());
        } else if (!exposeToken) {
            throw BusinessException.error("未配置账号邀请通知器，暂不能发出激活链接");
        }
        return new InvitationResponse(user.getId(), exposeToken ? activationUrl : "", invitation.getExpiresAt().toString());
    }

    private RoleDO resolveInviteRole(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return null;
        RoleDO role = roleMapper.findByCode(roleCode);
        if (role == null) throw BusinessException.error("角色不存在");
        if (!Boolean.TRUE.equals(role.getEnabled())) throw BusinessException.error("角色已停用");
        return role;
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

    private String generatedLegacyUsername(String normalizedEmail) {
        return "user-" + AuthService.sha256(normalizedEmail).substring(0, 16);
    }
}
