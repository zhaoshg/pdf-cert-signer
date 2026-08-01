# PDF 电子签章与证书管理系统

自主可控、合规防篡改的 PDF 电子签章平台，提供 X.509 数字证书全生命周期管理与 PAdES 标准密码学签章服务。

## 系统架构

```
┌──────────────────────────────────────────────────────┐
│              第三方业务系统（调用方）                    │
│  传入 PDF URL + 印章 URL + signer_id + 坐标           │
└──────────────────────┬───────────────────────────────┘
                       │ HTTP/REST
                       ▼
┌──────────────────────────────────────────────────────┐
│              Vue 3 管理后台 (cert-admin-ui)            │
│  证书管理 | PDF预览 + 印章拖拽 | 审计日志               │
└──────────────────────┬───────────────────────────────┘
                       │
                       ▼
┌──────────────────────────────────────────────────────┐
│          Spring Boot 3.2 后端 (多模块 Maven)           │
│  ┌─────────┐ ┌──────────┐ ┌─────────────────────┐    │
│  │ CA 服务  │ │ 签章引擎  │ │ 文件存储 (多 OBS)    │    │
│  │Bouncy   │ │PDFBox +  │ │ COS/Kodo/OSS/Local  │    │
│  │Castle   │ │Bouncy    │ │                     │    │
│  └─────────┘ └──────────┘ └─────────────────────┘    │
└──────────────────────────────────────────────────────┘
         │              │              │
         ▼              ▼              ▼
      MySQL 8.0     Redis 7.x     对象存储/本地磁盘
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Spring Boot 3.2, JDK 17, Maven 3.9 |
| 数据库 | MySQL 8.0, Redis 7.x |
| CA 与签名 | BouncyCastle 1.78, Apache PDFBox 3.0 |
| 安全 | Spring Security + JWT (jjwt 0.12) |
| 前端 | Vue 3, TypeScript, Ant Design Vue 4, pdfjs-dist 4, Vite 5 |

## 项目结构

```
pdf-cert-signer/
├── pom.xml                          # 根 POM (聚合模块 + 依赖管理)
├── cert-server-core/                # 公共模块: R<T>, BizException
├── cert-server-domain/              # 领域模块: JPA 实体, DTO, Repository
├── cert-server-infra/               # 基础设施: CA, 签章, 存储, 安全
├── cert-server-service/             # 业务服务: 证书管理, 签章编排
├── cert-server-api/                 # REST API: Controller + 全局异常处理
├── cert-server-bootstrap/           # 启动模块: Application + 配置
├── cert-admin-ui/                   # Vue 3 前端管理后台
├── CONTEXT.md                       # 领域模型词汇表
└── .spec-workflow/                  # 架构设计与任务拆分文档
```

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 7.x
- Maven 3.9+
- Node.js 18+

### 1. 初始化数据库

MySQL 中创建数据库（应用启动时自动建表）：

```sql
CREATE DATABASE IF NOT EXISTS cert_db DEFAULT CHARSET utf8mb4;
```

### 2. 配置文件

编辑 `cert-server-bootstrap/src/main/resources/application.yml`，修改数据库和 Redis 连接信息：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/cert_db
    username: root
    password: your_password
  data:
    redis:
      host: localhost
      port: 6379
```

### 3. 启动后端

```bash
# 设置 JDK 路径
set JAVA_HOME=D:\dev\jdk\jdk-17

# 编译 + 启动
mvn clean compile -DskipTests
mvn -pl cert-server-bootstrap spring-boot:run
```

服务启动后首次访问时会自动：
- 在 `conf/` 目录生成根证书 (root-ca.p12)
- 初始化管理员账号 `admin / admin123`

### 4. 启动前端

```bash
cd cert-admin-ui
npm install
npm run dev
```

访问 `http://localhost:3000`，使用 `admin / admin123` 登录。

## API 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/v1/auth/login` | 管理员登录，返回 JWT |
| `POST` | `/api/v1/cert/issue` | 签发数字证书，返回 signer_id |
| `GET` | `/api/v1/cert/list` | 证书列表（支持筛选） |
| `POST` | `/api/v1/cert/revoke/{id}` | 吊销证书 |
| `GET` | `/api/v1/cert/download/p12/{id}` | 下载 .p12 私钥证书 |
| `GET` | `/api/v1/cert/download/root-ca` | 下载根证书 .crt |
| `POST` | `/api/v1/pdf/sign` | 执行 PDF 密码学签章 |
| `GET` | `/api/v1/audit/list` | 审计日志列表 |

### 签发证书

```bash
curl -X POST http://localhost:8080/api/v1/cert/issue \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"creditCode":"1234567890","name":"张三","department":"财务部","email":"zhangsan@example.com","validDays":365}'
```

响应：

```json
{
  "code": 0,
  "message": "签发成功",
  "data": {
    "signerId": "CERT_20260801_A3F8",
    "serialNumber": "18f3a2b1c...",
    "certSubject": "CN=张三(财务部),OU=1234567890",
    "validFrom": "2026-08-01 00:00:00",
    "validTo": "2027-08-01 00:00:00"
  }
}
```

### 执行签章

```bash
curl -X POST http://localhost:8080/api/v1/pdf/sign \
  -H "Content-Type: application/json" \
  -d '{
    "signerId": "CERT_20260801_A3F8",
    "pdfUrl": "https://example.com/contract.pdf",
    "signatures": [{
      "pageIndex": 0,
      "sealUrl": "https://example.com/seal.png",
      "x": 100, "y": 200,
      "width": 120, "height": 120,
      "reason": "业务审批确认"
    }]
  }'
```

响应：

```json
{
  "code": 0,
  "message": "签署成功",
  "data": {
    "signedPdfUrl": "D:\\dev\\codes\\pdf-cert-signer\\.cert-storage\\signed_CERT_20260801_A3F8_1722...pdf",
    "certSubject": "CN=张三(财务部),OU=1234567890",
    "signTime": "2026-08-01T09:30:00"
  }
}
```

## 核心流程

### 证书签发

```
管理员 → 填写 credit_code + 姓名 + 部门
系统   → 生成 signer_id (CERT_YYYYMMDD_XXXX)
系统   → 生成 RSA 2048 密钥对
系统   → 根 CA 签发 X.509 证书（写入 signer_id 扩展）
系统   → 导出 PKCS#12 (.p12)，AES-256 加密存储
系统   → 若同 credit_code 已有有效证书，自动作废
系统   → 返回 signer_id（调用方保管，用于后续签章）
```

### PDF 签章

```
调用方 → 传入 signer_id + PDF URL + 印章 URL + 坐标
系统   → 通过 signer_id 查找 ACTIVE 证书
系统   → 校验证书状态（过期/吊销检查）
系统   → 下载 PDF → 下载印章图片
系统   → 坐标换算（像素 → PDF Point）
系统   → PDFBox + BouncyCastle PAdES 密码学签名
系统   → 上传签名后 PDF → 记录审计日志
系统   → 返回新 PDF URL
```

## 配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `storage.type` | `local` | 存储类型: local / cos / kodo / oss |
| `storage.local.base-path` | `.cert-storage` | 本地存储目录 |
| `cert.root-ca.keystore-path` | `conf/root-ca.p12` | 根证书存储路径 |
| `cert.default-valid-days` | `365` | 证书默认有效期（天） |
| `cert.tsa.url` | `https://freetsa.org/tsr` | 时间戳服务器地址 |
| `cert.tsa.timeout-seconds` | `10` | TSA 请求超时（秒） |
| `cert.tsa.fail-on-error` | `false` | TSA 失败时是否中断签章 |
| `jwt.expiration-hours` | `2` | JWT 过期时间（小时） |

## 文档

- [领域模型](CONTEXT.md)
- [需求规格说明书](docs/PRD.md)
- [架构设计](.spec-workflow/steering/product.md)
- [技术方案](.spec-workflow/steering/tech.md)
- [项目结构](.spec-workflow/steering/structure.md)
- [任务拆分](.spec-workflow/specs/pdf-cert-signer/tasks.md)
