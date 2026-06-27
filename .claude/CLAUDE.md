# CLAUDE.md — Smart-WorkFlow 项目约束

> 本文件是 Smart-WorkFlow 的工程宪法。所有代码生成(无论由 Claude、DeepSeek 还是
> 其他模型执行)必须遵守。违反任一硬约束的产物视为不合格,需返工。

---

## 0. 项目定位

低代码 OA 平台,内嵌 AI Agent 能力的多模块 Java 后端。
模块化单体(modular monolith),`-api`/`-biz` 拆分以支持未来微服务抽取。

**技术栈**:Java 21 · Spring Boot 3.4 · MyBatis-Plus · Flowable ·
dynamic-datasource-spring-boot3-starter · Flyway · jsqlparser · Quartz(单节点)

---

## 1. 模块结构与依赖方向(硬约束)

四层层级,依赖只能自上而下:

```
sw-dependencies (BOM)
  └─ sw-framework  : sw-common, sw-security
       └─ sw-basic : storage, notify, job, iot, knowledge, agent
            └─ sw-biz : system, form, workflow, openapi
                 └─ sw-bootstrap (启动 + Flyway + 自检)
```

> 注:原 `sw-biz-lowcode` 已整体重命名为 `sw-biz-form`(模块、包名、配置开关、
> 表前缀、AutoConfiguration 全部对齐到 form,不保留 lowcode 残留)。
> 重命名清单见附录 A。

**依赖铁律:**

- **业务模块永不依赖另一模块的 `-biz` 实现。** 只能依赖目标模块的 `-api`。
- `workflow` 依赖 `form`(不可反向)。workflow 通过
  `@AutoConfiguration(after = FormAutoConfiguration.class)` 在运行时门控,
  若 `sw.form.enabled` 非 true,fail-fast 抛 `IllegalStateException`。
- 每个 `sw-biz-*` 拆 `-api`(契约/DTO/SPI 接口) 与 `-biz`(实现),
  以便未来抽取微服务时最小重构。

**跨模块通信(强制模式):**

- **无返回值交互** → Spring 事件,从 `-api` 发布。
  事件必须 `@Async` + `@TransactionalEventListener(phase = AFTER_COMMIT)`。
  统一经 `DomainEventPublisher` 薄封装发布,保持机制可替换。
- **有返回值交互** → Facade 接口,定义于 `-api`,实现于 `-biz`,经 Spring 注入调用。
- ❌ **禁止**用全限定类名字符串选择实现(绕过 Spring 容器,破坏
  `@Transactional`/AOP,引入安全风险)。需多实现分发时,用
  `Map<String, Interface>` 以业务语义字符串为 key,或纯接口契约 + Spring 注入。

---

## 2. 模块职责边界(硬约束)

- **system** — 身份/组织/RBAC/字典/参数(共享内核)。**拥有**字典数据
  (dict type / dict item / code / label / 值域),经 `DictFacade`(定义于
  `sw-biz-system-api`)对外暴露。
- **form** — 仅承载**表单与控件库**。不拥有任何业务主数据。
  - 字典**控件**(下拉框:绑定哪个 dict type、单/多选、渲染)归 form;
    字典**数据**归 system。form 的字典控件经 `DictFacade` 消费 system 的字典数据,
    **禁止** form 直接访问 `sys_dict` 表。
- **workflow** — 流程引擎 + 外部数据源执行引擎(详见第 7 节)。
- **openapi** — 开放接口层。

---

## 3. 表命名规则(硬约束)

仅约束**主业务库**自建表。扩展库只做 CRUD、不建表、不拥有 schema,不受约束。

**原则:前缀 = 模块短名,一对一映射。唯一例外是 system 用 `sys_`。**

| 前缀 | 范围 | 模块 |
|------|------|------|
| `sys_` | 身份/组织/RBAC/字典/参数(共享内核) | sw-biz-system |
| `sw_form_` | 表单元数据、动态宽表、控件库 | sw-biz-form |
| `sw_workflow_` | 自建流程业务表、外部数据源执行审计表 | sw-biz-workflow |
| `sw_openapi_` | 应用、密钥、调用日志 | sw-biz-openapi |
| `sw_job_` | 任务定义、调度日志 | sw-basic-job |
| `sw_notify_` | 站内信、模板、发送记录 | sw-basic-notify |
| `sw_storage_` | 文件元数据 | sw-basic-storage |
| `sw_iot_` | 设备、产品、属性、上下行记录 | sw-basic-iot |
| `sw_knowledge_` | 知识库、文档、向量元数据 | sw-basic-knowledge |
| `sw_agent_` | 会话、消息、工具调用 | sw-basic-agent |
| `ACT_*` | Flowable 引擎自带表 | 框架自有,原样保留,**不纳入规则** |

**配套:**

- 关联表归单一模块,不跨模块共享 FK(如 `sys_user_role`、`sys_role_menu` 在 system 内)。
- ❌ **禁止自创前缀。** 新表前缀必须落在上表枚举内,否则视为违规。
- 现状 `sys_*` 核心表已就位,新增 system 表一律延续 `sys_`,**不得**改用 `system_`。
- 集群阶段才会引入的 `QRTZ_*` 同属框架自带表,届时原样保留,不纳入规则。

---

## 4. 低代码表单存储模型(硬约束)

### 4.1 存储模型:动态宽表

- 表单提交数据采用**动态宽表**:一个表单 = 一张物理表,**一行 = 一次提交**。
  不用单 JSON 列,不用 EAV。理由:查询/报表/导出/流程取值能力上限最高(原生 SQL + 索引)。
- 动态宽表是**「数据」不是「schema」**,**不归 Flyway 管**(见 6.2 例外)。
  环境迁移靠表单/应用/流程的导入导出功能(后续设计)。

### 4.2 物理表命名

| 用途 | 表名 |
|------|------|
| 主表单动态宽表 | `sw_form_{nanoId}` |
| 表格子表 | `sw_form_table_{nanoId}` |
| 配置表(全表单共用一张) | `sw_form_config`(form_id + 样式 definition jsonb) |
| 快照表(全表单共用一张) | `sw_form_snapshot`(form_id + version + definition jsonb) |

- **nanoId 规则**:首位强制小写字母 `[a-z]`,其余 `[a-z0-9]`,总长 ≤ 12。
  ❌ 不含大写(PG 折叠大小写 → 撞表)、不含 `-`、不以数字/下划线开头。
- ❌ 用户填写的"逻辑表名/字段名"**绝不能直接拼进 DDL**。物理表名由系统生成
  (`sw_form_` 前缀 + 受约束 nanoId);字段名须过严格白名单
  (`[a-z_][a-z0-9_]*`、长度限、保留字黑名单、不得撞 `sys_`/`sw_`/`ACT_`)。
  这是 SQL 注入红线。

### 4.3 关系原语:统一「宽表 + 外键」,`relation_type` 枚举分两档

表格控件与表单间关联**底层是同一个原语**(宽表 + 一个 bigint 外键列),
仅由 `relation_type` 区分行为:

| relation_type | 语义 | 删除 | 共享 | 渲染 | 子表命名 |
|---|---|---|---|---|---|
| `TABLE` | 主表单内嵌子表(明细行,如巡检清单) | CASCADE(删主→删明细) | 独占,不可被他表引用 | 内嵌、无独立样式 | `sw_form_table_{nanoId}` |
| `REFERENCE` | 独立表单间引用(如 IT申请→IT需求) | RESTRICT(有子引用则禁删父) | 可被多表引用 | 用目标表单自己的 definition 渲染 | 目标表单平级 `sw_form_{nanoId}` |

- 一对多的外键**永远落在「多」的一端(子表)**,子表存 `parent_record_id` / `apply_id`
  指回父。父表不持有指向多个子的列。
- 建表逻辑(DynamicTableManager)对两档**完全一致**(建宽表 + 加外键列);
  差异只在删除、渲染、命名三处,且均为查 `relation_type` 得到的属性,非分支硬编码。
- RESTRICT 校验在应用层删除路径显式执行(删父前查子表是否存在引用)。

### 4.4 字段类型(列生成)

基础类型:文本、数字、富文本、日期、布尔、字典(下拉)。
关系类型:`TABLE`(表格控件)、`REFERENCE`(关联控件)。
字典控件值经 `DictFacade` 校验是否在 dict type 值域内(见第 2 节)。

### 4.5 状态机:草稿 → 发布(硬约束)

- **草稿态**:只动元数据(`sw_form_config` / definition / 快照),
  **物理表零接触**。❌ 草稿保存路径中禁止出现任何 DDL 调用。
- **发布态**:发布动作 = 校验表名/字段名 → 建物理表/加列(DynamicTableManager)→ 冻结。
- **发布后表名、字段名不可修改**(改名 = DDL + 数据迁移,封死)。
- 用户在新建时可自定义逻辑表名/字段名,经白名单校验后由系统生成物理名。

### 4.6 快照与样式分离

- 表单版本快照 = 某版本 definition 的 JSON 全文,存 `sw_form_snapshot.definition`(jsonb)。
- 样式配置存 `sw_form_config.definition`(jsonb),与动态宽表(业务数据)分离。
- **「样式与数据分离」靠表职责划分实现,均存于主库**;不引入 MongoDB。
  Mongo 列为未来可选,仅当出现海量非结构化提交或超灵活 schema 需求时再评估。

---

## 5. 数据层与多租户(硬约束)

- **BaseEntity 仅适用于主业务库。** 其四个 MyBatis-Plus 拦截器只作用于主库;
  扩展库实体**禁止**继承 BaseEntity。动态宽表是否纳入拦截器范围需显式配置
  (它在主库内,但由 DynamicTableManager 管理,不走标准 Entity)。
- `TenantLineHandler` 必须**数据源感知**,在非主数据源上跳过租户注入。
- 多租户为**列级隔离**。
- 跨切面基础设施(多租户、BaseEntity、数据权限、Security 过滤链、字典服务)
  必须先于业务代码就位,避免昂贵的回填式改造。
- `DataScope` 枚举置于 `sw-common`,消除 sw-common 与 sw-security 间的脆弱字符串映射。

---

## 6. Schema 治理(Flyway)

### 6.1 通则

- **单数据源**,`{vendor}` 目录结构,PostgreSQL 为首个部署目标。
- ❌ 无 Liquibase,❌ 无 Hibernate DDL。
- 一个环境 = 一种固定数据库类型。CRUD 可移植性靠约定;DDL 可移植性靠
  per-vendor Flyway 脚本。扩展库只做 CRUD,永不拥有 schema。
- `prod-update` 目录存生产 DML 补丁(原生方言);失败**故意**阻断启动,留待开发者修复。
- 开发期以 **H2 作为 SQL 正确性代理**(跳过中间件验证)。

### 6.2 Flyway 例外:动态宽表

- 动态宽表(`sw_form_{nanoId}` / `sw_form_table_{nanoId}`)是运行时由
  DynamicTableManager 按 vendor 方言建立的**数据载体**,**不纳入 Flyway**。
- 这是对 "无 Hibernate DDL / DDL 全归 Flyway" 的**唯一显式例外**,有意为之。
- 固定元数据表(`sw_form_config`、`sw_form_snapshot` 及其它 `sys_*`/`sw_*` 表)
  仍全部归 Flyway。
- 动态宽表的环境迁移靠表单导入导出,不靠 Flyway 脚本。

---

## 7. 多数据源

- **编码期**:`@DS` 注解系统(经 dynamic-datasource-spring-boot3-starter)用于扩展库。
- **运行期**:动态 SQL 平台**仅限** `sw-biz-workflow`,只读(SELECT-only)仓库查询,
  附带加密、行数限制、超时、完整审计日志。

---

## 8. 安全 / 异常处理

- **过滤器层异常需区分**:令牌校验失败(401) vs 基础设施故障(500/503),
  不得把所有异常统一降级。
- ❌ **禁止远程代码执行类功能。** 不接受"用户上传代码包 → 主进程编译运行"。
  用户可配置的是**数据**(流程 key、表单数据、cron),不是**代码**。
  需要用户自部署任务逻辑时,只走进程外(执行器模型或 HTTP/webhook 触发)。
- ❌ 用户输入的表名/字段名禁止直接拼 DDL(见 4.2),须过白名单。

---

## 9. 定时任务(sw-basic-job)

- **技术选型**:Quartz,**单节点**(RAMJobStore,当前无 `QRTZ_*` 表)。
- 后台维护抽象 `JobHandler` 入口;实现类填 bean 名 + cron 表达式即可设置并启用。
- **任务类型经 `job_type` 枚举分流,共用同一套调度基础设施:**
  - `BEAN`:`bean_name` + `params`,走 JobHandler 入口。
  - `FLOW`:`flow_def_key` + `form_data`(JSON),**定时发起流程**。
- **FLOW 任务必须经与手动表单提交相同的校验路径**(字段合法性、必填、字典值域)。
  定时触发与手动提交**汇入同一个校验+发起方法**,不得各写一份,
  不得因"是定时任务配的"而跳过 FormSubmitService 校验。
- **job 不依赖 workflow 的 `-biz`,也不硬编流程逻辑。**
  到点发领域事件(如 `ScheduledFlowTriggerEvent`),workflow 监听并复用
  `FormSubmittedEventListener` 同一个流程发起入口。
- **集群升级路径(现在预留,后续做):**
  - 调度入口抽象,不把 RAMJobStore 写死在业务码,将来切 JDBC JobStore 只动配置。
  - 任务执行逻辑保持**幂等**,尤其 FLOW 任务以 `job_id + 触发时间` 做去重键。

**`sw_job_` 表(单节点版):**

- `sw_job_info` — `job_type`(BEAN/FLOW)、cron、状态、并发/misfire 策略;
  BEAN 存 `bean_name`+`params`,FLOW 存 `flow_def_key`+`form_data`(JSON)。
- `sw_job_log` — job_id、起止时间、状态、耗时、结果/异常、触发方式。

---

## 10. AI 协作工作流约束

- **架构判断前置到 prompt。** Claude 负责架构分析、时序约束识别、生成强约束执行 prompt;
  DeepSeek 负责代码生成。架构决策不留给生成阶段临场发挥。
- **DeepSeek 模型选择**:默认 V4 Flash(high thinking),仅在反复编译/Spring 装配失败时
  才升级 V4 Pro。
- **增量验证**:实现步骤之间执行 `mvn -q compile`。
- ❌ **Claude Code 反模式**:禁止用 `spring-boot:run` 作阻塞进程做启动校验
  (导致超时循环 + 端口冲突级联)。改用后台进程 + 日志轮询 + 干净进程终止 + 确定性结果码。

---

## 11. 开发哲学:Walking Skeleton

- 优先打通一条端到端的薄切片,而非任一模块的横向铺满。
- 里程碑:`登录 → 简单表单 → 单节点审批 → 通知` 端到端跑通,**之后**才横向扩展任一模块。
- 当前状态:登录链路(M02-F06-01)✅。下一环为表单定义+提交并发事件。
- form 模块第一阶段范围(skeleton):
  - 字典服务(归 system) → DynamicTableManager → 表单定义/发布 → 表单提交+发事件 →
    workflow 监听验证。
  - 关系原语两档(TABLE + REFERENCE)各跑一条最小路径,验 CASCADE 与 RESTRICT 两种删除语义。
  - REFERENCE 用真实场景 IT申请→IT需求;TABLE 用最简明细子表。

---

## 附录 A · lowcode → form 重命名清单

| 项 | 旧 | 新 |
|---|---|---|
| 模块目录 / artifactId | `sw-biz-lowcode` | `sw-biz-form` |
| 子模块 | `sw-biz-lowcode-api` / `-biz` | `sw-biz-form-api` / `-biz` |
| Java 包 | `*.lowcode.*` | `*.form.*` |
| AutoConfiguration | `LowcodeAutoConfiguration` | `FormAutoConfiguration` |
| 配置开关 | `sw.lowcode.enabled` | `sw.form.enabled` |
| workflow 门控引用 | `@AutoConfiguration(after = LowcodeAutoConfiguration.class)` | `@AutoConfiguration(after = FormAutoConfiguration.class)` |
| 存量表前缀 | `sw_lowcode_*` | `sw_form_*` |
| `AutoConfiguration.imports` | 旧全限定名 | 新全限定名 |
| CLAUDE.md 内全部 lowcode 字样 | — | 已对齐 form |
