package com.brad.pms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.brad.pms.common.enums.AssignmentType;
import com.brad.pms.common.enums.DataScopeType;
import com.brad.pms.common.enums.UserStatus;
import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Idempotent data backfill for the identity/organization model. This runner
 * never removes business data and can safely be called again after local seed
 * data has been created.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class EnterpriseDataMigration implements CommandLineRunner {

    private final UserMapper userMapper;
    private final OrgUnitTypeMapper orgUnitTypeMapper;
    private final OrgUnitMapper orgUnitMapper;
    private final PositionMapper positionMapper;
    private final UserPositionMapper userPositionMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final ProjectMapper projectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        ensureDictionaries();
        backfillUsersAndProjects();
    }

    /** Public so DataInitializer can complete the same backfill after creating demo users. */
    @Transactional
    public void backfillUsersAndProjects() {
        List<UserDO> users = userMapper.selectList(null);
        Map<String, Long> normalizedOwners = new HashMap<>();
        for (UserDO user : users) {
            String normalized = normalizeUsername(user.getUsername());
            if (normalized == null || normalized.isBlank()) {
                throw new IllegalStateException("用户 " + user.getId() + " 缺少有效英文名，无法完成身份迁移");
            }
            Long previous = normalizedOwners.putIfAbsent(normalized, user.getId());
            if (previous != null && !Objects.equals(previous, user.getId())) {
                throw new IllegalStateException("存在大小写不敏感的英文名冲突: " + normalized + " (用户 " + previous + " 与 " + user.getId() + ")");
            }
        }

        OrgUnitDO root = requireRoot();
        PositionDO defaultPosition = requirePosition("EMPLOYEE", "普通员工");
        for (UserDO user : users) {
            boolean changed = false;
            if ((user.getNameZh() == null || user.getNameZh().isBlank())
                    && user.getNickname() != null && !user.getNickname().isBlank()) {
                user.setNameZh(user.getNickname());
                changed = true;
            }
            if (!Objects.equals(user.getUsernameNormalized(), normalizeUsername(user.getUsername()))) {
                user.setUsernameNormalized(normalizeUsername(user.getUsername()));
                changed = true;
            }
            String normalizedEmail = normalizeEmail(user.getEmail());
            if (!Objects.equals(user.getEmailNormalized(), normalizedEmail)) {
                user.setEmailNormalized(normalizedEmail);
                changed = true;
            }
            if (user.getStatus() == null || user.getStatus().isBlank()) {
                user.setStatus(UserStatus.ACTIVE.name());
                changed = true;
            }
            if (user.getFailedLoginCount() == null) {
                user.setFailedLoginCount(0);
                changed = true;
            }
            if (user.getPasswordChangedAt() == null && user.getPassword() != null) {
                user.setPasswordChangedAt(user.getUpdatedAt() == null ? LocalDateTime.now() : user.getUpdatedAt());
                changed = true;
            }
            if (changed) userMapper.updateById(user);

            if (userPositionMapper.countActivePrimary(user.getId()) == 0) {
                UserPositionDO position = new UserPositionDO();
                position.setUserId(user.getId());
                position.setOrgUnitId(root.getId());
                position.setPositionId(defaultPosition.getId());
                position.setAssignmentType(AssignmentType.PRIMARY.name());
                position.setIsPrimary(true);
                position.setStartDate(LocalDate.now());
                position.setStatus("ACTIVE");
                userPositionMapper.insert(position);
            }

            String roleCode = user.getSystemRole() != null && user.getSystemRole() == 1 ? "SUPER_ADMIN" : "MEMBER";
            RoleDO role = roleMapper.findByCode(roleCode);
            if (role != null && userRoleMapper.findLiveByUserAndRole(user.getId(), roleCode).isEmpty()) {
                UserRoleDO grant = new UserRoleDO();
                grant.setUserId(user.getId());
                grant.setRoleId(role.getId());
                grant.setStartAt(LocalDateTime.now());
                grant.setStatus("ACTIVE");
                userRoleMapper.insert(grant);
            }
        }

        Long rootId = root.getId();
        projectMapper.update(null, new LambdaUpdateWrapper<ProjectDO>()
                .isNull(ProjectDO::getOrgUnitId)
                .set(ProjectDO::getOrgUnitId, rootId));
    }

    private void ensureDictionaries() {
        ensureOrgType("COMPANY", "公司", 0);
        ensureOrgType("BG", "业务群（BG）", 10);
        ensureOrgType("CENTER", "中心", 20);
        ensureOrgType("DEPARTMENT", "部门", 30);
        ensureOrgType("TEAM", "团队", 40);
        ensureOrgType("PDT", "项目团队（PDT）", 50);
        ensureOrgType("OTHER", "其他", 90);

        OrgUnitDO root = requireRoot();
        requirePosition("EMPLOYEE", "普通员工");
        requirePosition("MANAGER", "管理者");

        ensureRole("SUPER_ADMIN", "超级管理员", true, DataScopeType.ALL.name());
        ensureRole("ORG_ADMIN", "组织管理员", true, DataScopeType.ORG_AND_DESCENDANTS.name());
        ensureRole("BUSINESS_OWNER", "业务线负责人", true, DataScopeType.ORG_AND_DESCENDANTS.name());
        ensureRole("DEPT_MANAGER", "部门负责人", true, DataScopeType.ORG_AND_DESCENDANTS.name());
        ensureRole("PROJECT_ADMIN", "项目管理员", true, DataScopeType.ALL.name());
        ensureRole("PROJECT_MANAGER", "项目经理", true, DataScopeType.SELF.name());
        ensureRole("MEMBER", "普通员工", true, DataScopeType.SELF.name());

        Map<String, String> permissions = new LinkedHashMap<>();
        permissions.put("admin:user:read", "查看人员");
        permissions.put("admin:user:write", "编辑人员");
        permissions.put("admin:org:read", "查看组织架构");
        permissions.put("admin:org:write", "编辑组织架构");
        permissions.put("admin:role:read", "查看角色");
        permissions.put("admin:role:write", "编辑角色");
        permissions.put("admin:import:write", "导入组织与人员");
        permissions.put("admin:audit:read", "查看审计日志");
        permissions.put("project:read", "查看项目");
        permissions.put("project:create", "创建项目");
        permissions.put("project:write", "编辑项目");
        permissions.put("project:manage", "管理项目");
        permissions.put("project:comment:write", "发布项目评论");
        permissions.put("feedback:read", "查看反馈");
        permissions.put("feedback:write", "提交反馈");
        permissions.put("feedback:manage", "分诊与处理反馈");
        for (Map.Entry<String, String> entry : permissions.entrySet()) {
            PermissionDO permission = permissionMapper.selectOne(new LambdaQueryWrapper<PermissionDO>()
                    .eq(PermissionDO::getCode, entry.getKey()));
            if (permission == null) {
                permission = new PermissionDO();
                permission.setCode(entry.getKey());
                permission.setName(entry.getValue());
                permission.setResourceType(entry.getKey().startsWith("admin:") ? "MENU" : "API");
                permission.setSort(permissions.keySet().stream().toList().indexOf(entry.getKey()));
                permissionMapper.insert(permission);
            }
            ensureRolePermission("SUPER_ADMIN", permission.getId());
        }
        bindBuiltinPermissions(permissions);
        // Keep the root referenced so the method remains explicit when adding more
        // built-in dictionaries in future migrations.
        if (root == null) throw new IllegalStateException("无法创建公司总部组织");
    }

    private void bindBuiltinPermissions(Map<String, String> permissions) {
        Set<String> orgAdmin = Set.of("admin:user:read", "admin:user:write", "admin:org:read",
                "admin:org:write", "admin:role:read", "admin:import:write", "admin:audit:read", "project:read", "project:create", "project:write", "project:manage", "project:comment:write",
                "feedback:read", "feedback:write", "feedback:manage");
        Set<String> orgManagers = Set.of("admin:user:read", "admin:org:read", "project:read", "project:create", "project:write", "project:comment:write",
                "feedback:read", "feedback:write");
        Set<String> projectManagers = Set.of("project:read", "project:create", "project:write", "project:comment:write", "feedback:read", "feedback:write");
        Set<String> projectAdmins = Set.of("project:read", "project:create", "project:write", "project:manage", "project:comment:write", "feedback:read", "feedback:write");
        bindRolePermissions("ORG_ADMIN", orgAdmin, permissions);
        bindRolePermissions("BUSINESS_OWNER", orgManagers, permissions);
        bindRolePermissions("DEPT_MANAGER", orgManagers, permissions);
        bindRolePermissions("PROJECT_ADMIN", projectAdmins, permissions);
        bindRolePermissions("PROJECT_MANAGER", projectManagers, permissions);
        bindRolePermissions("MEMBER", Set.of("project:read", "project:create", "project:comment:write", "feedback:read", "feedback:write"), permissions);
        bindRolePermissions("SUPER_ADMIN", Set.of("feedback:read", "feedback:write", "feedback:manage"), permissions);
    }

    private void bindRolePermissions(String roleCode, Set<String> codes, Map<String, String> permissions) {
        RoleDO role = roleMapper.findByCode(roleCode);
        if (role == null) return;
        for (String code : codes) {
            PermissionDO permission = permissionMapper.selectOne(new LambdaQueryWrapper<PermissionDO>()
                    .eq(PermissionDO::getCode, code));
            if (permission != null) ensureRolePermission(roleCode, permission.getId());
        }
    }

    private void ensureRolePermission(String roleCode, Long permissionId) {
        RoleDO role = roleMapper.findByCode(roleCode);
        if (role != null && rolePermissionMapper.count(role.getId(), permissionId) == 0) {
            rolePermissionMapper.insert(role.getId(), permissionId);
        }
    }

    private void ensureOrgType(String code, String name, int sort) {
        OrgUnitTypeDO type = orgUnitTypeMapper.selectOne(new LambdaQueryWrapper<OrgUnitTypeDO>()
                .eq(OrgUnitTypeDO::getCode, code));
        if (type == null) {
            type = new OrgUnitTypeDO();
            type.setCode(code);
            type.setName(name);
            type.setSort(sort);
            type.setEnabled(true);
            orgUnitTypeMapper.insert(type);
        }
    }

    private OrgUnitDO requireRoot() {
        OrgUnitDO root = orgUnitMapper.findByCode("HQ");
        if (root != null) return root;
        OrgUnitTypeDO company = orgUnitTypeMapper.selectOne(new LambdaQueryWrapper<OrgUnitTypeDO>()
                .eq(OrgUnitTypeDO::getCode, "COMPANY"));
        root = new OrgUnitDO();
        root.setTypeId(company.getId());
        root.setCode("HQ");
        root.setName("公司总部");
        root.setSort(0);
        root.setStatus("ACTIVE");
        root.setPath("/");
        orgUnitMapper.insert(root);
        root.setPath("/" + root.getId() + "/");
        orgUnitMapper.updateById(root);
        return root;
    }

    private PositionDO requirePosition(String code, String name) {
        PositionDO position = positionMapper.findByCode(code);
        if (position != null) return position;
        position = new PositionDO();
        position.setCode(code);
        position.setName(name);
        position.setEnabled(true);
        positionMapper.insert(position);
        return position;
    }

    private void ensureRole(String code, String name, boolean builtin, String scope) {
        RoleDO role = roleMapper.findByCode(code);
        if (role == null) {
            role = new RoleDO();
            role.setCode(code);
            role.setName(name);
            role.setBuiltin(builtin);
            role.setDataScopeType(scope);
            role.setEnabled(true);
            roleMapper.insert(role);
        }
    }

    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
