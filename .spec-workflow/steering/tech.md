# Technology Stack — PDF 电子签章与证书管理系统

## Project Type

前后端分离的 Web 应用。后端为 Spring Boot REST API 服务，前端为 Vue 3 SPA 管理后台。系统同时作为 API Server 嵌入第三方业务系统中。

## Core Technologies

### Primary Language(s)

| 层级 | 语言 | 版本 |
|------|------|------|
| 后端 | Java | 17 LTS |
| 前端 | TypeScript | 5.x |
| 构建 | Maven | 3.9+ |

### Key Dependencies/Libraries

#### 后端依赖

| 依赖 | 用途 | 版本参考 |
|------|------|----------|
| **spring-boot-starter-web** | REST API 框架 | 3.2.x |
| **spring-boot-starter-data-jpa** | ORM / 数据库访问 | 3.2.x |
| **spring-boot-starter-security** | 管理员认证与鉴权 | 3.2.x |
| **spring-boot-starter-validation** | 请求参数校验 | 3.2.x |
| **jjwt (io.jsonwebtoken)** | JWT Token 签发与验证 | 0.12.x |
| **mysql-connector-j** | MySQL JDBC 驱动 | 8.x |
| **spring-boot-starter-data-redis** | Redis 缓存客户端 | 3.2.x |
| **bouncycastle (bcprov + bcpkix)** | X.509 证书生成、密钥管理 | 1.78 |
| **EU DSS (digital-signature-service)** | PAdES 密码学签章引擎 | 6.x |
| **knife4j (springdoc-openapi)** | API 文档 UI | 2.x |
| **lombok** | 简化 POJO 代码 | 1.18.x |
| **tencent-cos-java-sdk** | 腾讯云 COS SDK | 5.6.x |
| **qiniu-java-sdk** | 七牛 Kodo SDK | 7.x |
| **aliyun-sdk-oss** | 阿里云 OSS SDK | 3.x |

#### 前端依赖

| 依赖 | 用途 | 版本参考 |
|------|------|----------|
| **Vue 3** | UI 框架 | 3.4.x |
| **TypeScript** | 类型安全 | 5.x |
| **Ant Design Vue** | UI 组件库 | 4.x |
| **pdfjs-dist** | PDF 前端渲染与预览 | 4.x |
| **Axios** | HTTP 请求 | 1.7.x |
| **Pinia** | 状态管理 | 2.x |
| **Vue Router** | 前端路由 | 4.x |
| **Vite** | 构建工具 | 5.x |

### Application Architecture

```
┌─────────────────────────────────────────────┐
│                 Browser (Web UI)             │
│  Vue 3 + Ant Design Vue + pdfjs-dist        │
│  │                                            │
│  ├─ 证书管理页 (签发/列表/吊销/下载)           │
│  └─ 签章画布页 (PDF 预览 + 印章拖拽)          │
└──────────────────┬──────────────────────────┘
                   │ HTTP/REST (JSON) + JWT
                   ▼
┌─────────────────────────────────────────────┐
│          Spring Boot (Monolithic)            │
│                                              │
│  ┌─────────────────────────────────────┐    │
│  │         Controller Layer            │    │
│  │  CertController / PdfSignController │    │
│  └──────────────┬──────────────────────┘    │
│                 │                             │
│  ┌──────────────▼──────────────────────┐    │
│  │          Service Layer              │    │
│  │  CertService / SigningService       │    │
│  │  AuditService / FileStorageService  │    │
│  └──────────────┬──────────────────────┘    │
│                 │                             │
│  ┌──────────────▼──────────────────────┐    │
│  │        Infrastructure Layer         │    │
│  │  JPA Repository / Redis / OBS SDK   │    │
│  └─────────────────────────────────────┘    │
│                                              │
│  ┌─────────────────────────────────────┐    │
│  │       Signing Engine (核心)          │    │
│  │  EU DSS PAdES + BouncyCastle        │    │
│  │  下载PDF → 校验身份 → 坐标换算       │    │
│  │  → TSA时间戳 → 密码学签名 → 上传     │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

### Data Storage

| 存储类型 | 技术选型 | 用途 |
|----------|----------|------|
| **关系型数据库** | MySQL 8.0 | 证书信息、审计日志、管理员账户 |
| **缓存** | Redis 7.x | 证书查询缓存、签章临时状态、分布式锁（可选） |
| **文件存储** | 腾讯云 COS / 七牛 Kodo / 阿里云 OSS / 本地磁盘 | 签名后 PDF 持久化存储 |
| **临时文件** | 本地磁盘 `java.io.tmpdir` | 签章过程中临时下载的 PDF，定时清理（cron 24h） |

### External Integrations

| 外部系统 | 协议 | 说明 |
|----------|------|------|
| **第三方业务系统** | HTTP/REST | 调用方通过 API 发起签章，传入 PDF URL + 印章 URL |
| **RFC 3161 TSA Server** | HTTP (Timestamp Protocol) | 获取可信时间戳 Token（可配置多个 TSA URL） |
| **OBS 厂商** | HTTPS / SDK | 上传签名后 PDF 文件 |

### Authentication

| 层级 | 机制 | 说明 |
|------|------|------|
| 管理后台登录 | JWT (无状态) | 管理员用户名 + 密码登录，签发 JWT，过期时间 2h |
| API 内部调用 | API Key / mTLS (预留) | 第三方系统调用签章 API 的身份校验（一期可简化） |
| 证书私钥保护 | AES-256-CBC | 数据库中 `.p12` 文件以 AES-256 加密存储，解密密钥从环境变量/配置中心注入 |

## Development Environment

### Build & Development Tools

| 工具 | 用途 |
|------|------|
| **Maven 3.9** | Java 项目构建、依赖管理 |
| **Vite 5** | 前端 HMR 开发服务器 |
| **JDK 17** | Java 编译与运行 |
| **Docker Compose** (可选) | 本地启动 MySQL + Redis 开发环境 |

### Code Quality Tools

| 工具 | 用途 |
|------|------|
| **Checkstyle** | Java 代码规范检查（阿里巴巴规约） |
| **ESLint + Prettier** | 前端代码规范与格式化 |
| **JUnit 5 + Mockito** | 后端单元测试 |
| **Vitest** | 前端单元测试 |

### Version Control & Collaboration

| 项目 | 说明 |
|------|------|
| **VCS** | Git，托管于 GitHub |
| **分支策略** | `main` 稳定分支 + `feature/xxx` 功能分支 + `fix/xxx` 修复分支 |
| **提交规范** | `type: description`（如 `feat: 新增证书签发接口`） |

## Deployment & Distribution

- **目标平台**：Linux (CentOS / Ubuntu)，Java 17 运行时
- **分发方式**：Maven 打包为可执行 JAR (`mvn clean package -DskipTests`)
- **前端部署**：`npm run build` 产出静态资源，部署至 Nginx 或嵌入 Spring Boot static 目录
- **安装要求**：JDK 17、MySQL 8.0、Redis 7.x

## Technical Requirements & Constraints

### Performance Requirements

| 指标 | 目标 | 实现策略 |
|------|------|----------|
| 签章响应 P50 | < 1.5s | 异步 TSA 请求池、PDF 流式签名、缓存证书信息 |
| PDF 预览首屏 | < 1.5s | PDF.js 按需渲染当前页、OBS 直读（或 CDN 加速） |
| 并发 QPS | 50+ | Tomcat 线程池调优、数据库连接池 (HikariCP)、Redis 证书缓存 |
| 临时文件清理 | 定时任务 | `@Scheduled` cron，每 6 小时清理超过 24h 的临时文件 |

### Compatibility Requirements

- **JDK 版本**：JDK 17 LTS（最低）
- **MySQL 版本**：8.0+
- **浏览器**：Chrome 90+、Edge 90+、Firefox 110+
- **PDF 标准**：PAdES Baseline-B (ISO 32000-2)，兼容 Adobe Reader XI+

### Security & Compliance

| 维度 | 方案 |
|------|------|
| **私钥加密** | AES-256-CBC 加密存储，解密密钥由环境变量 `CERT_KEY_SECRET` 注入 |
| **传输安全** | 全链路 HTTPS |
| **JWT 安全** | HMAC-SHA256 签名，AccessToken 2h 过期，签发时间校验 |
| **防篡改** | PAdES 标准签名，Adobe Reader 可自动检测 PDF 篡改 |
| **审计追溯** | 签章操作全字段记录，日志不可删除 |
| **SQL 注入** | JPA 参数化查询 + Hibernate 防注入 |
| **敏感信息** | 数据库密码、OBS AK/SK、根证书私钥均通过环境变量/配置中心注入，不写入代码 |

### Scalability & Reliability

- **水平扩展**：Spring Boot 无状态服务，可通过多实例 + 负载均衡（Nginx）横向扩展。签章请求无 Session 绑定。
- **数据库**：单机 MySQL，未来可升级为主从读写分离。
- **Redis**：单机 / 哨兵模式，缓存证书信息，提升签署时证书查询性能。

## Technical Decisions & Rationale

### Decision Log

1. **Spring Boot 3.2 单体架构** — 本系统功能边界清晰（证书管理 + 签章），无复杂分布式事务需求。单体架构降低部署与运维复杂度，符合"简单优先"原则。

2. **EU DSS 而非自写签章逻辑** — EU DSS 是欧盟官方维护的 PAdES 实现库，内置 PKCS#7 签名结构、时间戳嵌入、OCSP/CRL 校验，避免自行实现复杂的签名字典写入。相比原生 BouncyCastle + PDFBox 方案，代码量减少 60% 以上且合规性更强。

3. **BouncyCastle 用于 CA 操作** — 证书签发（生成密钥对、X.509 扩展写入）使用 BouncyCastle。EU DSS 内部也封装了 BouncyCastle，两者一致。

4. **策略模式适配多 OBS** — 定义 `FileStorageService` 接口，通过 `@ConditionalOnProperty("storage.type")` 加载对应的 COS/Kodo/OSS/Local 实现，实现代码不收口、新增厂商只需加一个实现类。

5. **JWT 无状态认证** — 管理后台仅管理员使用，无复杂角色体系，JWT 足够。避免 Session 同步问题，无状态便于水平扩展。

6. **Vue 3 + Ant Design Vue** — 企业级后台管理系统首选组合，Ant Design Vue 提供表格、表单、弹窗等丰富组件，适合证书管理与签章画布场景。

7. **证书缓存至 Redis** — 签章时需高频查询 signer 的 ACTIVE 证书，将证书信息缓存至 Redis（Key: `cert:{signerId}`），签发/吊销时主动失效，减少数据库查询。

## Known Limitations

- **无 CRL/OCSP 在线校验**：一期不实现证书吊销列表发布和在线状态查询，吊销状态仅通过数据库判断。未来可扩展。
- **无分布式事务**：签章涉及多个步骤（下载→签名→上传→记录日志），某一步骤失败时需补偿回滚（临时文件清理、已上传文件删除），未使用 Saga 模式。
- **无文件并发锁**：本系统不对 PDF 文件加并发写锁，若调用方未控制好并发，可能产生冗余签名版本。责任由调用方承担。
- **TSA 单点依赖**：时间戳服务依赖外部 TSA URL 可用性，一期支持配置降级策略（超时后可选择报错或跳过时间戳）。
