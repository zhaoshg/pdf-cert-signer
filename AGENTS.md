# AGENTS.md — PDF 电子签章与证书管理系统

## 环境速查

- **JDK**: 必须 `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"`（PATH 上有 JDK 25，Lombok 会失效）
- **Maven**: `D:\dev\tools\apache-maven-3.9.11\bin\mvn.cmd`
- **编译**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"; mvn clean compile -DskipTests`
- **启动**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"; mvn spring-boot:run -pl cert-server-bootstrap`
- **前端**: Vite dev server port 3800，代理到 `localhost:8080`

## 待开发任务

### 3. PdfSigner.createSignature 增加 reason 参数
- 修改前端页面（PDF签章页面），增加reason输入框。

### 4. 场景证书签章
- 业务场景：临时生成短期证书 → 签章 → 立即废弃
- 签章参数需增加证书签发必填字段（certType、creditCode、姓名、部门等）
- 后端：签章前先用传入参数调用 CertificateIssuer 签发临时证书（短期，如1天），签章后标记 REVOKED
- 临时证书的 p12Data 仅用于本次签章，不返回给调用方

### 5. 签章接口增加审计字段
- SignRequest 增加 `authTicket`（调用方系统鉴权凭证）、`requestIp`（请求来源IP）
- cert_audit_log 表增加字段：auth_ticket、request_ip、signed_file_path（输出文件保存路径）
- 签章完成后将上述信息写入审计日志，便于后续追溯

### 6. 证据上链
- 签章完成后，将已签章文件和场景信息打包，上区块链存证
- 场景信息包括：证书指纹、签章时间、TSA 时间戳令牌、审计字段（authTicket、requestIp）等
- 使用开放联盟链，候选：BSN（区块链服务网络）或百度超级链（XuperChain）
- 上链后返回交易哈希（txHash），写入 cert_audit_log 或独立的链上存证记录表
- 提供链上存证验证接口：传入 txHash，返回存证数据及链上确认状态

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
