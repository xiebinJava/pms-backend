# AI 只读上下文：隔离数据库证明记录

日期：2026-10-14。测试类：src/test/java/com/brad/pms/ai/connector/context/AiWorkflowContextReadOnlyIntegrationTest.java。

## 运行方式

~~~bash
PMS_CONTEXT_READ_ISOLATED=true \
DOCKER_HOST=unix:///Users/fs/.colima/fsclaw/docker.sock \
TESTCONTAINERS_HOST_OVERRIDE=127.0.0.1 \
TESTCONTAINERS_RYUK_DISABLED=true \
mvn -Dtest=AiWorkflowContextReadOnlyIntegrationTest test
~~~

说明：本机 Docker daemon 由 colima 挂载于用户目录 socket（非 /var/run/docker.sock），
testcontainers 需要 DOCKER_HOST/TESTCONTAINERS_HOST_OVERRIDE 指向实际环境；这两个值只是
连接参数，不降低任何隔离要求，容器仍由本次测试自己创建、迁移、销毁（禁用 Ryuk）。

## 结果与证据

- 打开 gate：Tests run 3, Failures 0, Errors 0, Skipped 0，退出 0（约 21s，含容器启动）。
  - `createFixturesThroughBusinessServicesBeforeTracking`：项目/需求经 ProjectService/RequirementManagementService
    真实业务流创建（管理员身份），fixture 阶段写入发生在任何追踪窗口打开之前。
  - `contextAndResourceReadsLeaveZeroBusinessWrites`：project legacy/focused + requirement + 四类变体
    resource 读取完成后：
    - MutationTracker 捕获（MyBatis update 拦截器 + JDBC DataSource 代理 execute/executeBatch）：
      **writes=[] locks=[]**；
    - 业务表（project/project_node/pms_requirement）数量/状态纯读前后一致；
    - requirement 工作流行数窗口内不变（该行是 create 阶段的业务事实，读取不触碰）。
  - `resourceReadSeesRepeatableReadSnapshotWithoutLocksAndNotifiesLaterWrites`：REPEATABLE READ
    隔离级读事务在屏障 SELECT 后，另一线程 UPDATE project.progress=99 并提交；窗口内再次读取
    仍为旧值（RR 生效、无锁感染）；新请求读到新值。RR 断言与无写追踪窗口分离，未混用。
- 关闭 gate：同一命令重跑 Tests run 3, Skipped 3 —— 类整体 skip（JUnit 缓存报告），无容器启动、无连接，
  skip 未计入通过。
- 记录内容仅包含计数与 SQL 分类（insert/update/lock 标记），不含表数据正文、用户身份或凭据。

## 环境参数（ reproduction ）

- MySQL 8.4 容器（mysql:8.4 镜像），driver com.mysql.cj.jdbc.Driver 经 @DynamicPropertySource 覆盖，
  Flyway 迁移在容器内执行到最新版本（V60 等）。
- 该证据证明的是当前切片（project/requirement，legacy/focused，context + resource）的纯读与 RR 行为；
  其余组件矩阵在 Task 8 扩展并重跑本类。
