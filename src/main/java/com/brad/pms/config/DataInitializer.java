package com.brad.pms.config;

import com.brad.pms.entity.*;
import com.brad.pms.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 种子数据：首次启动自动创建默认管理员账号与演示数据
 * 默认账号：admin / admin123
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper memberMapper;
    private final ProjectTaskMapper taskMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectCommentMapper commentMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    @Transactional
    public void run(String... args) {
        if (userMapper.selectCount(null) != null && userMapper.selectCount(null) > 0) {
            return;
        }
        log.info("初始化种子数据：admin / admin123");

        UserDO admin = user("admin", "admin123", "管理员", "admin@pms.com");
        UserDO zhang = user("zhangsan", "admin123", "张三", "zhangsan@pms.com");
        UserDO li = user("lisi", "admin123", "李四", "lisi@pms.com");
        UserDO wang = user("wangwu", "admin123", "王五", "wangwu@pms.com");

        ProjectDO p1 = new ProjectDO();
        p1.setName("开源项目管理平台");
        p1.setDescription("基于 Vue3 + Spring Boot 的项目管理系统，覆盖项目、任务、里程碑与评论管理。");
        p1.setStatus(1);
        p1.setPriority(2);
        p1.setOwnerId(admin.getId());
        p1.setStartDate(LocalDate.now().minusDays(20));
        p1.setEndDate(LocalDate.now().plusDays(80));
        p1.setProgress(35);
        p1.setCode("PRJ-000001");
        projectMapper.insert(p1);

        ProjectDO p2 = new ProjectDO();
        p2.setName("电商中台重构");
        p2.setDescription("对现有电商中台进行服务化改造与性能优化。");
        p2.setStatus(0);
        p2.setPriority(1);
        p2.setOwnerId(zhang.getId());
        p2.setStartDate(LocalDate.now().plusDays(10));
        p2.setEndDate(LocalDate.now().plusDays(120));
        p2.setProgress(0);
        p2.setCode("PRJ-000002");
        projectMapper.insert(p2);

        member(p1.getId(), admin.getId(), 0);
        member(p1.getId(), zhang.getId(), 1);
        member(p1.getId(), li.getId(), 2);
        member(p1.getId(), wang.getId(), 2);
        member(p2.getId(), zhang.getId(), 0);
        member(p2.getId(), li.getId(), 1);

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

        task(p1.getId(), "搭建后端工程骨架", "Spring Boot + MyBatis-Plus + JWT", 2, 2, zhang.getId(), m1.getId(), 1);
        task(p1.getId(), "搭建前端工程骨架", "Vite + Vue3 + ant-design-vue + UnoCSS", 2, 2, li.getId(), m1.getId(), 2);
        task(p1.getId(), "实现项目 CRUD 接口", "含分页、搜索与负责人", 2, 1, zhang.getId(), m1.getId(), 3);
        task(p1.getId(), "实现任务看板", "支持拖拽切换状态", 1, 2, li.getId(), m1.getId(), 4);
        task(p1.getId(), "里程碑管理", "列表与状态流转", 0, 1, wang.getId(), m1.getId(), 5);
        task(p1.getId(), "撰写 README 与部署文档", "含 Docker 与生产部署说明", 0, 0, admin.getId(), m2.getId(), 6);

        ProjectCommentDO c1 = new ProjectCommentDO();
        c1.setProjectId(p1.getId());
        c1.setContent("欢迎加入开源项目管理平台，开发过程中有任何问题随时在评论区讨论。");
        c1.setUserId(admin.getId());
        commentMapper.insert(c1);
    }

    private UserDO user(String username, String rawPassword, String nickname, String email) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(encoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setEmail(email);
        userMapper.insert(user);
        return user;
    }

    private void member(Long projectId, Long userId, int role) {
        ProjectMemberDO m = new ProjectMemberDO();
        m.setProjectId(projectId);
        m.setUserId(userId);
        m.setRole(role);
        memberMapper.insert(m);
    }

    private void task(Long projectId, String title, String desc, int status, int priority,
                      Long assigneeId, Long milestoneId, int sort) {
        ProjectTaskDO t = new ProjectTaskDO();
        t.setProjectId(projectId);
        t.setTitle(title);
        t.setDescription(desc);
        t.setStatus(status);
        t.setPriority(priority);
        t.setAssigneeId(assigneeId);
        t.setMilestoneId(milestoneId);
        t.setSort(sort);
        taskMapper.insert(t);
    }
}
