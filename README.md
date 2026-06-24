# Smart-WorkFlow

**嵌入 Agent 的低代码 OA 系统后端基础框架**

Smart-WorkFlow 是一个面向未来的企业级低代码 OA 系统后端，深度集成 AI Agent 能力，支持流程自动化、IoT 设备接入、知识库与 RAG 检索，致力于打造智能化的企业办公协同平台。

## 当前项目结构

```
Smart-WorkFlow/
├── pom.xml                                  # 根父工程，聚合所有模块
├── README.md
├── sw-module-dependencies/                   # 公共依赖版本管理模块
│   └── pom.xml
└── sw-bootstrap/                             # 唯一启动入口模块
    ├── pom.xml
    └── src/main/
        ├── java/com/sw/ck/bootstrap/
        │   └── StarterApplication.java       # 启动类
        └── resources/
            ├── application.yml               # 主配置文件
            └── application-dev.yml           # 开发环境配置
```

## 技术栈规划

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言 | Java 21 | 使用 LTS 版本，支持虚拟线程 |
| 框架 | Spring Boot 3.4.x | 最新稳定版 |
| 持久层 | MyBatis-Plus 3.5.x | 增强型 MyBatis |
| 连接池 | Alibaba Druid | 监控与性能 |
| 数据库迁移 | Flyway | 自动化版本管理 |
| 数据库 | MySQL + PostgreSQL | 双库支持 |
| 流程引擎 | Flowable 7.x | 兼容 Spring Boot 3 |
| AI Agent | Spring AI + LangGraph4j | Agent 编排 |
| IoT | Spring Integration MQTT + Paho v5 | 硬件设备接入 |
| 存储 | MinIO | 对象存储 |
| 知识库 | Apache Tika + PDFBox + Jsoup | 文档解析 |
| 向量检索 | pgvector | RAG 检索 |
| 缓存 | Redis | 高性能缓存 |
| 文档 | Springdoc OpenAPI | API 文档 |
| 监控 | Micrometer Tracing + OpenTelemetry | 可观测性 |
| 工具 | Lombok + MapStruct + Hutool + JWT | 生产力工具 |

> **说明：** 当前初版仅搭建了基础工程骨架，上述技术栈对应的业务模块将在后续按需创建。

## 依赖管理说明

### sw-module-dependencies（公共依赖版本管理）

本模块专门负责统一管控所有第三方依赖版本，遵循以下原则：

1. **BOM 优先：** Spring Boot 和 Spring AI 生态依赖通过官方 BOM 统一管理。
2. **版本收敛：** 第三方依赖版本统一在 `sw-module-dependencies/pom.xml` 的 `<properties>` 中声明，各业务模块不写版本。
3. **按需引入：** 子模块需要什么依赖就在自己的 `pom.xml` 中声明 artifactId 即可。

### 版本策略

- 使用 Spring Boot BOM 管理 Spring Boot 生态依赖版本。
- 使用 Spring AI BOM 管理 Spring AI 依赖版本。
- 第三方依赖版本统一在 `sw-module-dependencies` 的 properties 中维护。
- 不依赖 SNAPSHOT 版本，优先使用稳定版本。

> **注意：** 如果某些依赖版本与实际环境不兼容，可按需调整 `sw-module-dependencies/pom.xml` 中的版本号。

## 构建方式

```bash
# 构建全部模块
mvn clean install -DskipTests

# 仅构建并启动
mvn clean package -DskipTests
```

## 启动方式

```bash
# 方式一：使用 Spring Boot Maven Plugin
cd sw-bootstrap
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 方式二：编译后直接运行 Jar
cd sw-bootstrap
mvn clean package -DskipTests
java -jar target/sw-bootstrap.jar --spring.profiles.active=dev
```

### 启动要求

- JDK 21+
- Maven 3.8+
- MySQL 8.0+（如需启动数据库相关功能）
- Redis 7.0+（如需启动缓存相关功能）

## 配置说明

### 配置文件结构

| 文件 | 用途 | 说明 |
|------|------|------|
| `application.yml` | 主配置 | 包含全部配置项骨架和占位 |
| `application-dev.yml` | 开发环境 | 覆盖开发环境特定配置 |

### 关键配置项

所有敏感信息（密码、Token、API Key）均使用占位符，通过环境变量或启动参数注入：

```bash
# 通过环境变量注入
export DATASOURCE_USERNAME=root
export DATASOURCE_PASSWORD=your_password
export OPENAI_API_KEY=sk-your-key

# 或通过启动参数传入
java -jar sw-bootstrap.jar \
  --DATASOURCE_USERNAME=root \
  --DATASOURCE_PASSWORD=your_password
```

### 配置项说明

| 配置前缀 | 说明 | 当前状态 |
|----------|------|----------|
| `spring.datasource` | 数据源 | 骨架配置，需填充 |
| `spring.druid` | Druid 连接池 | 骨架配置 |
| `spring.flyway` | 数据库迁移 | 默认关闭，需启用 |
| `spring.data.redis` | Redis 缓存 | 骨架配置 |
| `spring.flowable` | 流程引擎 | 骨架配置 |
| `spring.ai` | AI 能力 | 骨架配置 |
| `spring.mqtt` | MQTT 接入 | 骨架配置 |
| `spring.minio` | 文件存储 | 骨架配置 |
| `mybatis-plus` | 持久层 | 骨架配置 |
| `management` | Actuator 监控 | 基础配置 |
| `springdoc` | API 文档 | 基础配置 |

## 后续模块扩展建议

后续业务模块按需手动创建，命名规范为 `sw-module-xxx`，包路径统一使用 `com.sw.ck.xxx`。

### 推荐创建顺序

| 模块 | 包路径 | 说明 |
|------|--------|------|
| `sw-module-common` | `com.sw.ck.common` | 公共工具类、常量、通用返回体 |
| `sw-module-security` | `com.sw.ck.security` | 认证与授权（Spring Security + JWT） |
| `sw-module-system` | `com.sw.ck.system` | 系统管理（用户、角色、菜单、组织） |
| `sw-module-workflow` | `com.sw.ck.workflow` | 流程引擎（Flowable 集成） |
| `sw-biz-form` | `com.sw.ck.form` | 低代码（表单、页面、数据源、元数据） |
| `sw-module-agent` | `com.sw.ck.agent` | AI Agent（Spring AI + LangGraph4j） |
| `sw-module-knowledge` | `com.sw.ck.knowledge` | 知识库（RAG、文档解析、向量检索） |
| `sw-module-iot` | `com.sw.ck.iot` | IoT 设备接入（MQTT） |
| `sw-module-storage` | `com.sw.ck.storage` | 文件存储（MinIO） |
| `sw-module-job` | `com.sw.ck.job` | 定时任务 |

### 模块依赖原则

- 新模块在根 `pom.xml` 中添加 `<module>` 声明。
- 新模块在 `sw-module-dependencies` 中进行依赖版本管理。
- 需要引入业务模块时，先在 `sw-module-dependencies/pom.xml` 中添加该模块的 dependencyManagement（如果模块间有依赖关系）。
- 业务模块之间如需相互依赖，通过根 POM 的 dependencyManagement 管理版本。

### 添加新模块示例

```xml
<!-- 根 pom.xml 添加模块 -->
<modules>
    <module>sw-module-dependencies</module>
    <module>sw-bootstrap</module>
    <module>sw-module-system</module>
</modules>

<!-- sw-bootstrap 引用业务模块 -->
<dependency>
    <groupId>com.sw.ck</groupId>
    <artifactId>sw-module-system</artifactId>
</dependency>
```

## 开发约定

### 包路径命名

| 模块 | artifactId | 包路径 |
|------|-----------|--------|
| 依赖管理 | `sw-module-dependencies` | 无 Java 代码 |
| 启动入口 | `sw-bootstrap` | `com.sw.ck.bootstrap` |
| 系统管理 | `sw-module-system` | `com.sw.ck.system` |
| 流程引擎 | `sw-module-workflow` | `com.sw.ck.workflow` |
| 低代码 | `sw-biz-form` | `com.sw.ck.form` |
| Agent | `sw-module-agent` | `com.sw.ck.agent` |
| IoT | `sw-module-iot` | `com.sw.ck.iot` |

### Maven 依赖约定

- 所有第三方依赖版本号只在 `sw-module-dependencies/pom.xml` 中声明。
- 各业务模块的 `pom.xml` 不写第三方依赖版本，通过父工程继承和 dependencyManagement 统一控制。
- Spring Boot Maven Plugin 只在 `sw-bootstrap` 中配置，其他模块不配置。

---

**Smart-WorkFlow** — 始于智能，不止于流程。
