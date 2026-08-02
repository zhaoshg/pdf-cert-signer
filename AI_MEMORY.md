# AI_MEMORY.md — 跨会话开发状态传递

---

## 环境要求

- **JDK 17**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"`（PATH 上有 JDK 25，Lombok 会失效，必须显式切 JAVA_HOME）
- **Maven**: `D:\dev\tools\apache-maven-3.9.11\bin\mvn.cmd`
- **编译命令**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"; mvn clean compile -DskipTests`
- **启动命令**: `$env:JAVA_HOME = "D:\dev\jdk\jdk-17"; mvn spring-boot:run -pl cert-server-bootstrap`
- **前端**: Vite dev server port 3800，代理到 `localhost:8080`

---

## 当前目标

按优先级依次开发，详见 `AGENTS.md` 第 11-43 行：

1. 印章缩放
2. Bug修复：PDF 缩放后签章位置偏移
3. PdfSigner.createSignature 增加 reason 参数
4. 场景证书签章（临时签发短期证书→签章→废弃）
5. 签章接口增加审计字段（authTicket、requestIp）
6. 证据上链（BSN 或百度超级链）

---

## 已完成 / 代码现状

最近一次大版本提交 `c955888`（feat: 视觉签章重构、TSA时间戳集成、PDF代理加载、签名元数据完善），19 个文件，+676/-69：

### 核心变更文件

| 文件 | 变更内容 |
|------|---------|
| `cert-server-infra/.../signing/PdfSigner.java` | 视觉签章重构：添加 Prop_Build 字典、COSName.getPDFName() 引用；签名元数据完善 |
| `cert-server-infra/.../signing/TsaClient.java` | TSA 时间戳客户端（签名时间权威溯源） |
| `cert-server-infra/.../http/HttpClients.java` | **新文件**：共享 SSL 绕过 HttpClient（`TRUST_ALL` 常量），PdfSignController 和 SigningServiceImpl 均引用它 |
| `cert-server-api/.../api/PdfSignController.java` | PDF 代理端点（通过服务器拉取远程 PDF 避免前端跨域）、引用 HttpClients.TRUST_ALL |
| `cert-server-service/.../impl/SigningServiceImpl.java` | 签章服务实现，引用 HttpClients.TRUST_ALL |
| `cert-server-infra/.../ca/CertificateIssuer.java` | 证书签发 |
| `cert-server-infra/.../ca/RootCaManager.java` | 根 CA 管理 |
| `cert-admin-ui/src/views/signing/SignPage.vue` | 外部用户签章页（183 行新增） |
| `cert-admin-ui/src/views/signing/SignCanvas.vue` | 管理员签章画布 |
| `cert-server-bootstrap/.../application.yml` | DB/Redis 凭据全面环境变量化（`${ENV_VAR:localhost}` 占位符，无明文密码） |
| `seal-test.html` | 签章测试页面 |

### 后续提交

| 提交 | 内容 |
|------|------|
| `221f87b` | .gitignore 忽略生成的 root-ca.p12 密钥文件 |
| `8c1017e` | AGENTS.md（环境速查+待办+文件索引） |
| `f45969b` | AGENTS.md 增加任务 6「证据上链」 |
| `3bae3e5` | 清理 serena 冗余记忆文件 |

---

## 遇到的坑 / 避坑指南

1. **COSName 构造函数不可用**：`new COSName("Prop_Build")` 报错，必须用 `COSName.getPDFName("Prop_Build")`。PdfSigner.java 中已修复。
2. **Lombok 在 JDK 25 下失效**：编译报错找不到 getter/setter 时，先检查 JAVA_HOME 是否指向 JDK 17。`mvn clean compile` 必须带 `-DskipTests`。
3. **application.yml 明文凭据**：已全面环境变量化，默认值均为 localhost/root 等本地占位符。**严禁**把真实 IP/密码提交到仓库。上一 session 曾因本地调试残留真实 IP 被 `git checkout` 还原。
4. **Serena 记忆与 AGENTS.md 重复**：已删除 `.serena/memories/pdf-cert-signer/todo-next.md`，待办以 AGENTS.md 为唯一来源。
5. **HttpClient 重复创建**：PdfSignController 和 SigningServiceImpl 原先各自 new 一个 SSL 绕过 HttpClient，现已统一抽取到 `HttpClients.TRUST_ALL`。
6. **CertController 导入顺序**：代码审查指出导入顺序不一致，已还原为 `项目→jakarta→spring` 风格。

---

## 下一步操作

新会话接手后，先编译验证环境：

```powershell
$env:JAVA_HOME = "D:\dev\jdk\jdk-17"
mvn clean compile -DskipTests
```

确认 `BUILD SUCCESS` 后，阅读 `AGENTS.md`，从**任务 1（印章缩放）**开始开发。

整个 AGENTS.md 中详细列出了每项任务涉及的具体文件和改动范围，开发时直接参照即可。
