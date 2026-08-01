# Project Structure — PDF 电子签章与证书管理系统

## Directory Organization

```
pdf-cert-signer/
├── cert-server/                       # Spring Boot 后端主模块（聚合 POM）
│   ├── cert-server-core/              # 核心模块：公共工具、异常、统一响应
│   │   └── src/main/java/com/example/cert/core/
│   │       ├── common/                # 公共类 (R<T>, PageResult, BaseEntity)
│   │       ├── exception/             # 业务异常 + 全局异常处理
│   │       └── util/                  # 工具类
│   ├── cert-server-domain/            # 领域模块：实体、VO、DTO、Repository
│   │   └── src/main/java/com/example/cert/domain/
│   │       ├── entity/                # JPA 实体 (Admin, Cert, AuditLog)
│   │       ├── dto/                   # 请求/响应 DTO
│   │       ├── vo/                    # 视图 VO
│   │       ├── enums/                 # 枚举 (CertStatus, StorageType)
│   │       └── repository/            # JPA Repository 接口
│   ├── cert-server-service/           # 业务服务模块：Service 接口与实现
│   │   └── src/main/java/com/example/cert/service/
│   │       ├── CertService / impl/    # 证书签发、查询、吊销、下载
│   │       ├── SigningService / impl/ # PDF 签章主流程编排
│   │       ├── AuditService / impl/   # 审计日志记录
│   │       └── FileStorageService     # 文件存储接口
│   │           ├── impl/CosStorageServiceImpl
│   │           ├── impl/KodoStorageServiceImpl
│   │           ├── impl/OssStorageServiceImpl
│   │           └── impl/LocalStorageServiceImpl
│   ├── cert-server-infra/             # 基础设施模块
│   │   └── src/main/java/com/example/cert/infra/
│   │       ├── ca/                    # CA 服务 (BouncyCastle)
│   │       │   ├── RootCaManager      # 根证书加载/生成
│   │       │   └── CertificateIssuer  # 签发用户证书
│   │       ├── signing/               # PDF 签章引擎 (EU DSS)
│   │       │   ├── PdfSigner          # PAdES 封装
│   │       │   ├── TsaClient          # TSA 时间戳客户端
│   │       │   └── CoordinateConverter # 坐标换算
│   │       ├── storage/               # OBS SDK 适配
│   │       ├── security/              # Spring Security 配置 + JWT 工具
│   │       └── config/                # 全局配置 (Redis, 线程池, 定时任务)
│   ├── cert-server-api/               # Controller 层：REST API 入口
│   │   └── src/main/java/com/example/cert/api/
│   │       ├── CertController         # 证书管理接口
│   │       ├── PdfSignController      # 签章接口
│   │       └── AuthController         # 管理员登录
│   └── cert-server-bootstrap/         # 启动模块：配置 + Application 入口
│       └── src/main/resources/
│           ├── application.yml        # 通用配置
│           ├── application-dev.yml    # 开发环境
│           ├── application-prod.yml   # 生产环境
│           └── logback-spring.xml     # 日志配置
│
├── cert-admin-ui/                     # Vue 3 管理后台
│   ├── src/
│   │   ├── api/                       # Axios 接口封装
│   │   ├── views/
│   │   │   ├── login/                 # 登录页
│   │   │   ├── certificate/           # 证书管理页（列表/签发/详情）
│   │   │   ├── signing/               # PDF 签章画布页（预览+拖拽）
│   │   │   └── audit/                 # 审计日志页
│   │   ├── components/                # 公共组件
│   │   ├── stores/                    # Pinia 状态管理
│   │   ├── router/                    # Vue Router 路由
│   │   └── utils/                     # 工具函数
│   ├── package.json
│   └── vite.config.ts
│
├── docs/                              # 文档
│   ├── CONTEXT.md                     # 领域模型
│   └── PRD.md                         # 需求规格说明书
├── .spec-workflow/                    # Spec 工作流
│   ├── steering/                      # 架构指导文档 (product/tech/structure)
│   └── specs/                         # 功能规格文档
├── .gitignore
├── pom.xml                            # 根 POM
└── README.md
```

## Module Dependency Graph

```
cert-server-bootstrap
    ├── cert-server-api
    │       ├── cert-server-service
    │       │       ├── cert-server-domain
    │       │       └── cert-server-infra
    │       └── cert-server-core
    └── (Spring Boot Starter)

依赖方向：bootstrap → api → service → domain + infra → core
         (上层依赖下层，core 不依赖任何业务模块)
```

## Naming Conventions

### Files & Packages

| 类型 | 规范 | 示例 |
|------|------|------|
| Java 包名 | 全小写 | `com.example.cert.service` |
| Java 类名 | 大驼峰 | `CertController`, `PdfSigner` |
| Vue 组件 | 大驼峰，`/views/` 下文件夹 | `CertList.vue`, `SignCanvas.vue` |
| 配置文件 | 小写 + 短横线 | `application-dev.yml` |

### Java Code

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `CertificateService`, `AuditLog` |
| 方法名 | camelCase | `issueCertificate()`, `findActiveBySignerId()` |
| 变量名 | camelCase | `signerId`, `certificateChain` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_VALID_DAYS`, `CERT_STATUS_ACTIVE` |
| 包名 | 全小写 | `com.example.cert.service` |

### TypeScript Code

| 类型 | 规范 | 示例 |
|------|------|------|
| 接口/类型 | PascalCase | `SignRequest`, `CertListItem` |
| 函数/变量 | camelCase | `fetchCertList`, `pdfUrl` |
| 组合式函数 | camelCase + `use` 前缀 | `usePdfRenderer`, `useStampDrag` |
| 组件 | PascalCase | `CertTable`, `SealOverlay` |

## Import Patterns

### Java Import Order (IDEA 默认)
1. `import static` 静态导入
2. `java.*` / `javax.*` 标准库
3. `org.springframework.*` / 第三方框架
4. `com.example.cert.*` 项目内部

### TypeScript Import Order
1. Vue/Vite 核心 (`vue`, `vue-router`, `pinia`)
2. UI 组件库 (`ant-design-vue`)
3. 第三方库 (`pdfjs-dist`, `axios`)
4. 项目内部 (`@/api/*`, `@/components/*`)

## Code Structure Patterns

### Java Controller 文件组织
```java
@RestController
@RequestMapping("/api/v1/cert")
public class CertController {
    // 1. 注入 Service
    // 2. @PostMapping / @GetMapping 方法
    //    - 校验参数
    //    - 调用 Service
    //    - 包装统一响应 R<T>
}
```

### Java Service 文件组织
```java
public interface CertService {
    CertVO issue(IssueRequest request);
    PageResult<CertVO> list(CertQuery query);
    void revoke(Long certId);
    byte[] download(Long certId);
}
```

### Vue 视图文件组织
```vue
<script setup lang="ts">
// 1. imports
// 2. reactive state
// 3. computed
// 4. methods / lifecycle hooks
</script>

<template>
  <!-- 模板内容 -->
</template>

<style scoped>
  /* 样式 */
</style>
```

## Code Organization Principles

1. **面向接口编程** — Service 层定义接口，实现类放入 `impl/` 子包。
2. **单一职责** — 每个 Service 只负责一个领域。Controller 不做业务逻辑，仅参数校验 + 调用 Service + 返回结果。
3. **分层依赖** — Controller → Service → Repository，不可反向依赖。Domain 层不依赖任何上层模块。
4. **统一响应** — 所有 API 返回 `R<T>` 格式 (`{ code, message, data }`)。
5. **异常统一处理** — 业务异常通过 `@RestControllerAdvice` 全局捕获，无需在每个 Controller 中 try-catch。

## Module Boundaries

| 模块 | 职责 | 可依赖 |
|------|------|--------|
| **core** | 公共工具、统一响应体、基础异常 | 无（最底层） |
| **domain** | JPA 实体、DTO/VO、枚举、Repository | core |
| **infra** | CA 证书、签章引擎、OBS 适配、安全 | core、domain |
| **service** | 业务逻辑编排 | core、domain、infra |
| **api** | REST Controller | core、service |
| **bootstrap** | 启动配置、Application 入口 | 所有模块 |

## Code Size Guidelines

| 类型 | 上限 | 说明 |
|------|------|------|
| 单个 Java 文件 | 500 行 | 超出考虑拆分 |
| 单个方法 | 80 行 | 超出考虑提取私有方法 |
| 单个 Vue 组件 | 400 行 | 超出考虑提取子组件 |

## Database Naming Conventions

| 对象 | 规范 | 示例 |
|------|------|------|
| 表名 | 小写 + 下划线，单数 | `cert_admin`, `cert_certificate`, `cert_audit_log` |
| 列名 | 小写 + 下划线 | `signer_id`, `created_at` |
| 主键 | `id` (自增) | — |
| 索引 | `idx_表名_列名` | `idx_cert_certificate_sign_id` |
| 唯一约束 | `uk_表名_列名` | `uk_cert_certificate_serial` |
| 时间字段 | `created_at`, `updated_at` | — |

## Documentation Standards

- 所有 Service 接口的公开方法加 Javadoc（说明参数、返回值、异常）。
- 复杂业务逻辑（如签章流程编排、坐标换算）加行内注释说明。
- 数据库 DDL 脚本存放于 `cert-server-bootstrap/src/main/resources/db/` 目录。
