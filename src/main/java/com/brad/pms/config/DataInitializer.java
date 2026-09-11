package com.brad.pms.config;

import com.brad.pms.entity.*;
import com.brad.pms.common.enums.SystemRole;
import com.brad.pms.dto.response.ProjectNodeDTO;
import com.brad.pms.mapper.*;
import com.brad.pms.service.NodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Local demo seed data. Persistent (mysql/prod) empty databases create one
 * administrator from PMS_BOOTSTRAP_* env vars, falling back to the documented
 * OSS defaults (admin@example.com / PmsAdmin123!). Production must override.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectCommentMapper commentMapper;
    private final NodeService nodeService;
    private final EnterpriseDataMigration enterpriseDataMigration;
    private final Environment environment;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) {
        Long userCount = userMapper.selectCount(null);
        if (userCount != null && userCount > 0) {
            enterpriseDataMigration.backfillUsersAndProjects();
            return;
        }

        if (isPersistentProfile()) {
            // Documented OSS first-login defaults (must match distribution README / .env.example).
            String email = bootstrapOrDefault("PMS_BOOTSTRAP_ADMIN_EMAIL", "admin@example.com");
            String normalizedEmail = EnterpriseDataMigration.normalizeEmail(email);
            if (normalizedEmail == null || !normalizedEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new IllegalStateException("PMS_BOOTSTRAP_ADMIN_EMAIL 必须是有效邮箱");
            }
            String username = bootstrapOrDefault("PMS_BOOTSTRAP_ADMIN_USERNAME", "admin");
            String nameZh = bootstrapOrDefault("PMS_BOOTSTRAP_ADMIN_NAME_ZH", "系统管理员");
            String password = bootstrapOrDefault("PMS_BOOTSTRAP_ADMIN_PASSWORD", "PmsAdmin123!");
            if (password.length() < 12) {
                throw new IllegalStateException("PMS_BOOTSTRAP_ADMIN_PASSWORD 至少需要 12 位");
            }
            if ("production".equalsIgnoreCase(System.getenv("PMS_DEPLOYMENT_ENV"))
                    && "PmsAdmin123!".equals(password)) {
                throw new IllegalStateException(
                        "生产环境禁止使用公开默认管理员密码 PmsAdmin123!，请设置 PMS_BOOTSTRAP_ADMIN_PASSWORD");
            }
            UserDO bootstrap = user(username, password, nameZh, email);
            bootstrap.setSystemRole(SystemRole.ADMINISTRATOR.getCode());
            userMapper.updateById(bootstrap);
            enterpriseDataMigration.backfillUsersAndProjects();
            log.info("已创建企业初始管理员邮箱: {}", email);
            return;
        }

        log.info("初始化测试演示数据（登录名：admin，密码：admin123，仅限测试配置）");

        UserDO admin = user("admin", "admin123", "管理员", "admin@pms.com");
        admin.setSystemRole(SystemRole.ADMINISTRATOR.getCode());
        userMapper.updateById(admin);
        List<UserDO> demoUsers = createDemoUsers();
        UserDO brad = demoUsers.get(0);
        UserDO terry = demoUsers.get(1);
        UserDO kevin = demoUsers.get(2);
        UserDO claire = demoUsers.get(3);

        ProjectDO p1 = new ProjectDO();
        p1.setName("开源项目管理平台");
        p1.setDescription("基于 Vue3 + Spring Boot 的项目管理系统，覆盖项目、任务、里程碑与评论管理。");
        p1.setStatus(1);
        p1.setPriority(2);
        p1.setOwnerId(admin.getId());
        p1.setCreatedBy(admin.getId());
        p1.setStartDate(LocalDate.now().minusDays(20));
        p1.setEndDate(LocalDate.now().plusDays(80));
        p1.setProgress(35);
        p1.setCode("PRJ-000001");
        projectMapper.insert(p1);
        nodeService.initDefault(p1.getId(), admin.getId());
        List<ProjectNodeDTO> p1Nodes = nodeService.list(p1.getId());

        ProjectDO p2 = new ProjectDO();
        p2.setName("电商中台重构");
        p2.setDescription("对现有电商中台进行服务化改造与性能优化。");
        p2.setStatus(0);
        p2.setPriority(1);
        p2.setOwnerId(terry.getId());
        p2.setCreatedBy(terry.getId());
        p2.setStartDate(LocalDate.now().plusDays(10));
        p2.setEndDate(LocalDate.now().plusDays(120));
        p2.setProgress(0);
        p2.setCode("PRJ-000002");
        projectMapper.insert(p2);
        nodeService.initDefault(p2.getId(), terry.getId());

        member(p1.getId(), admin.getId(), 0);
        member(p1.getId(), brad.getId(), 1);
        member(p1.getId(), terry.getId(), 2);
        member(p1.getId(), kevin.getId(), 2);
        member(p2.getId(), terry.getId(), 0);
        member(p2.getId(), claire.getId(), 1);

        ProjectMilestoneDO m1 = new ProjectMilestoneDO();
        m1.setProjectId(p1.getId());
        m1.setTitle("MVP 交付");
        m1.setDescription("完成项目、任务、里程碑、成员、评论五大模块");
        m1.setDueDate(LocalDate.now().plusDays(45));
        m1.setStatus(1);
        milestoneMapper.insert(m1);

        ProjectMilestoneDO m2 = new ProjectMilestoneDO();
        m2.setProjectId(p1.getId());
        m2.setTitle("开源发布");
        m2.setDescription("补充文档、测试与 CI，发布到 GitHub");
        m2.setDueDate(LocalDate.now().plusDays(90));
        m2.setStatus(0);
        milestoneMapper.insert(m2);

        task(p1.getId(), nodeId(p1Nodes, "kickoff"), "立项材料评审", "确认目标、范围与资源授权", 2, 2, admin.getId(), 1);
        task(p1.getId(), nodeId(p1Nodes, "requirement"), "梳理需求与验收标准", "汇总需求并形成范围基线", 2, 1, terry.getId(), 2);
        task(p1.getId(), nodeId(p1Nodes, "design"), "完成技术方案评审", "记录关键方案决策", 2, 1, kevin.getId(), 3);
        task(p1.getId(), nodeId(p1Nodes, "develop"), "搭建后端工程骨架", "Spring Boot + MyBatis-Plus + JWT", 1, 2, terry.getId(), 4);
        task(p1.getId(), nodeId(p1Nodes, "develop"), "实现任务看板", "支持拖拽切换状态", 0, 2, claire.getId(), 5);
        task(p1.getId(), nodeId(p1Nodes, "knowledge"), "撰写 README 与部署文档", "含 Docker 与生产部署说明", 0, 0, admin.getId(), 6);

        ProjectCommentDO c1 = new ProjectCommentDO();
        c1.setProjectId(p1.getId());
        c1.setContent("欢迎加入开源项目管理平台，开发过程中有任何问题随时在评论区讨论。");
        c1.setUserId(admin.getId());
        commentMapper.insert(c1);

        enterpriseDataMigration.backfillUsersAndProjects();
    }

    private UserDO user(String username, String rawPassword, String nickname, String email) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setUsernameNormalized(EnterpriseDataMigration.normalizeUsername(username));
        user.setPassword(encoder.encode(rawPassword));
        user.setNameZh(nickname);
        user.setNickname(nickname);
        user.setEmail(email);
        user.setEmailNormalized(EnterpriseDataMigration.normalizeEmail(email));
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private List<UserDO> createDemoUsers() {
        String[][] seeds = {
                {"张伟", "Alex.Zhang"}, {"李强", "Terry.Li"}, {"周岚", "Linda.Zhou"}, {"陈宇", "Kevin.Chen"},
                {"王璇", "Claire.Wang"}, {"赵晨", "Ethan.Zhao"}, {"刘洋", "Andy.Liu"}, {"孙悦", "Nina.Sun"},
                {"黄凯", "Kyle.Huang"}, {"吴倩", "Grace.Wu"}, {"徐凡", "Frank.Xu"}, {"何敏", "Mia.He"},
                {"高远", "Owen.Gao"}, {"郑琳", "Alice.Zheng"}, {"林浩", "Leo.Lin"}, {"郭婷", "Tina.Guo"},
                {"唐杰", "Jason.Tang"}, {"沈薇", "Vivian.Shen"}, {"彭博", "Eric.Peng"}, {"宋妍", "Yuki.Song"}
        };
        return java.util.Arrays.stream(seeds)
                .map(seed -> user(seed[1], "admin123", seed[0],
                        EnterpriseDataMigration.normalizeUsername(seed[1]) + "@pms.com"))
                .toList();
    }

    private boolean isPersistentProfile() {
        return java.util.Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> "mysql".equalsIgnoreCase(profile)
                        || "prod".equalsIgnoreCase(profile)
                        || "production".equalsIgnoreCase(profile));
    }

    private String optionalBootstrap(String key) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String bootstrapOrDefault(String key, String defaultValue) {
        String value = optionalBootstrap(key);
        return value != null ? value : defaultValue;
    }

    private void member(Long projectId, Long userId, int role) {
        ProjectMemberDO m = new ProjectMemberDO();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        memberMapper.insert(m);
    }

    private Long nodeId(List<ProjectNodeDTO> nodes, String nodeKey) {
        return nodes.stream()
                .filter(node -> nodeKey.equals(node.getNodeKey()))
                .map(ProjectNodeDTO::getId)
                .findFirst()
                .orElseThrow();
    }

    private void task(Long projectId, Long nodeId, String title, String desc, int status, int priority,
                      Long assigneeId, int sort) {
        ProjectTaskDO t = new ProjectTaskDO();
        t.setProjectId(projectId);
        t.setNodeId(nodeId);
        t.setTitle(title);
        t.setDescription(desc);
        t.setStatus(status);
        t.setPriority(priority);
        t.setAssigneeId(assigneeId);
        t.setSort(sort);
        taskMapper.insert(t);
    }
}
