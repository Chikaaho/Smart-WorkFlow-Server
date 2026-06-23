项目:Smart-WorkFlow(com.sw.ck:smart-workflow),嵌入 AI Agent 的低代码 OA 系统后端。
技术栈:Java 21 + Spring Boot 3.4 + Maven 多模块 + MyBatis-Plus + Flowable + Redis。

模块分层(严格单向依赖,无环;biz 模块间只依赖对方的 -api):
smart-workflow
├── sw-dependencies   BOM
├── sw-framework      内核层
│   ├── sw-common     基础工具:异常体系、R/PageResult、MyBatis-Plus 配置、Redis 工具
│   └── sw-security   认证授权骨架:JWT、LoginUser、LoginUserHolder、UserDetailsProvider(SPI)
├── sw-basic          基础能力层(IoT/Job/Knowledge/Notify/Storage/Agent)
├── sw-biz            业务层
│   ├── sw-biz-system   组织架构/权限/字典/监控(目前仅占位)
│   ├── sw-biz-workflow 流程引擎(Flowable),运行期依赖 lowcode
│   ├── sw-biz-lowcode  低代码表单
│   └── sw-biz-openapi  开放接口
└── sw-bootstrap      唯一启动入口

约定:动手前先读相关现有文件,沿用现有包结构与命名风格,不重复造轮子。
完成后编译/能启动再汇报,列出新增/修改文件清单和关键决策。

数据访问约定:每个聚合 = Entity(extends BaseEntity)+ Mapper(extends BaseMapperX)
  + Service(extends BaseService<T>)+ ServiceImpl(extends BaseServiceImpl)。
简单 CRUD 一律用 MP lambda 链(lambdaQuery/lambdaUpdate),不手写 SQL;复杂查询才落 XML/注解。
Service 接口落在 -biz,不放 -api(api 不含 DB 依赖);模块间调用走 -api 的 SPI。

多数据源约定（dynamic-datasource-spring-boot3-starter 4.3.1）:
- 主库 key = "master"，已绑定 Flyway（唯一做迁移的库）和租户拦截器（TenantLineHandler 仅对 master 生效）。
- 扩展数据源通过 spring.datasource.dynamic.datasource.<key> 声明，使用 @DS("<key>") 切源。
- 扩展库实体不继承 BaseEntity（不带 tenant_id/deleted/version 注解），逻辑删除/乐观锁/租户隔离
  均为注解驱动——不加注解即天然不触发。
- 扩展库不纳入 Flyway（建表由 DBA 或外部工具管理，prod-update 目录仅对主库生效）。
- 跨数据源无本地事务：同一方法用 @DS 切源写多库，出错不会一起回滚。需一致性的走
  事件/补偿 或显式接受不一致；严禁跨数据源套同一个 @Transactional 指望一起回滚。
- 租户拦截器已改造为 DS 感知：CommonTenantLineHandler.ignoreTable() 检测当前 DS，
  非 master 时对所有表返回 true（不追加 tenant_id 条件），杜绝扩展库查询被注入
  tenant_id=?。

外部数据源执行引擎约定:
- 元数据表 wf_external_datasource 存连接信息（密码 AES-256-GCM 加密），继承 BaseEntity 走主库租户隔离。
- 密钥从环境变量 SW_CIPHER_KEY 注入（Base64 编码 32 字节），不进代码、不进库、不进日志。
- 执行引擎（SqlExecutor）：独立 JDBC 通道（HikariCP 池，不复用主库 SqlSessionFactory/
  dynamic-datasource/任何 MP 拦截器）。
- SQL 安全：jsqlparser 解析 → 仅放行单条 SELECT → 禁止堆叠（分号）→ 黑名单兜底。
- 强制 setMaxRows + setQueryTimeout + setReadOnly（连接级 + Statement 级）。
- 每次执行落审计 wf_sql_execution_audit（操作人、数据源、SQL 原文、返回行数、成功/失败）。
- 执行入口挂 @PreAuthorize("@ss.hasPermi('workflow:datasource:execute')") 高权限点。
