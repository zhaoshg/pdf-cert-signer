# AGENTS.md — PDF 电子签章与证书管理系统

## 环境速查

- **JDK**: 必须 `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"`（PATH 上有 JDK 25，Lombok 会失效）
- **Maven**: `D:\dev\tools\apache-maven-3.9.11\bin\mvn.cmd`
- **编译**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"; mvn clean compile -DskipTests`
- **启动**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"; mvn spring-boot:run -pl cert-server-bootstrap`
- **前端**: Vite dev server port 3800，代理到 `localhost:8080`

## 待开发任务

### 1. 印章缩放
- 签章时印章应支持缩放调整大小
- 前端 SignCanvas/SignPage 需增加缩放控件（拖拽角/滑块）

### 2. Bug修复：PDF缩放后签章位置偏移
- 复现：PDF 缩放后拖拽印章，签章后实际位置与用户所见不一致
- 原因推测：坐标未按缩放比例换算
- 需在 CoordinateConverter 或前端坐标计算中考虑 zoom 因子

### 3. PdfSigner.createSignature 增加 reason 参数
- `signature.setReason(reason)` 当前写死为 "PDF电子签章"
- 改为从签章接口参数传入，支持调用方自定义签章原因
- 涉及：SignRequest DTO、PdfSigner.sign()、PdfSignController、前端表单

### 4. 场景证书签章
- 业务场景：临时生成短期证书 → 签章 → 立即废弃
- 签章参数需增加证书签发必填字段（certType、creditCode、姓名、部门等）
- 后端：签章前先用传入参数调用 CertificateIssuer 签发临时证书（短期，如1天），签章后标记 REVOKED
- 临时证书的 p12Data 仅用于本次签章，不返回给调用方

### 5. 签章接口增加审计字段
- SignRequest 增加 `authTicket`（调用方系统鉴权凭证）、`requestIp`（请求来源IP）
- cert_audit_log 表增加字段：auth_ticket、request_ip、signed_file_path（输出文件保存路径）
- 签章完成后将上述信息写入审计日志，便于后续追溯

## 关键文件索引

- `cert-server-infra/.../PdfSigner.java` — 核心签章逻辑（视觉签章、数字签名、TSA 时间戳）
- `cert-server-infra/.../TsaClient.java` — TSA 时间戳客户端
- `cert-server-infra/.../CertificateIssuer.java` — 证书签发
- `cert-server-infra/.../HttpClients.java` — 共享 SSL 绕过 HttpClient
- `cert-server-api/.../PdfSignController.java` — 签章 + PDF 代理端点
- `cert-server-service/.../SigningServiceImpl.java` — 签章服务
- `cert-admin-ui/src/utils/sealUtils.ts` — 印章生成工具
- `cert-admin-ui/src/views/signing/SignCanvas.vue` — 管理员签章页
- `cert-admin-ui/src/views/signing/SignPage.vue` — 外部用户签章页
- `docs/adr/0001-auto-generate-seal-image.md` — 印章自动生成 ADR
