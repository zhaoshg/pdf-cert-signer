# Tasks — PDF 电子签章与证书管理系统

- [x] **P1.1** 创建 Maven 多模块项目骨架
  - 根 POM + 6 个子模块 (core/domain/infra/service/api/bootstrap)
  - Spring Boot 3.2 + JDK 17
  - 验证: `mvn clean compile -DskipTests` 通过
  - _Leverage: structure.md 模块依赖图_
  - _Prompt: 创建 Maven 多模块项目骨架，根 POM 聚合 6 个子模块，配置 Spring Boot 3.2 parent、JDK 17、依赖版本管理。子模块间依赖关系: bootstrap → api → service → domain + infra → core。验证 `mvn clean compile` 通过。_

- [x] **P1.2** 实现 core 模块：统一响应 + 异常处理
  - 文件: cert-server-core/ R.java, BizException.java, GlobalExceptionHandler.java
  - 验证: 编译通过，R<T> 格式 {code, message, data}
  - _Leverage: structure.md 统一响应规范_

- [x] **P1.3** 配置 bootstrap 模块：Spring Boot 入口 + 数据库
  - 文件: Application.java, application.yml, application-dev.yml
  - MySQL 8.0 + Redis 7.x 连接配置
  - 验证: 服务启动成功，数据库连接正常

- [x] **P2.1** 实现 domain 模块：JPA 实体 + 枚举
  - 实体: Admin, Certificate, AuditLog
  - 枚举: CertStatus (ACTIVE/REVOKED), StorageType
  - JPA Repository 接口
  - DDL 脚本 (src/main/resources/db/)
  - 验证: 编译通过，DDL 可执行

- [x] **P2.2** 实现 infra/ca: 根证书管理 + 证书签发 (BouncyCastle)
  - RootCaManager: 加载/生成根证书
  - CertificateIssuer: 生成密钥对 → 签发 X.509 → 写入 signer_id 扩展 → 导出 PKCS#12
  - SignerIdGenerator: "CERT_" + 日期 + 4位随机码
  - AES-256 加密 .p12 存储
  - 验证: 单元测试验证证书签发 + 根证书链验证通过

- [x] **P3.1** 实现 infra/security: JWT + Spring Security
  - JwtUtil: 签发/验证 Token
  - SecurityConfig: 配置拦截规则
  - 初始化管理员账号 (admin/admin123)
  - 验证: 登录接口返回 JWT，未登录接口返回 401

- [x] **P4.1** 实现 service: 证书管理服务
  - CertServiceImpl: 签发(自动作废旧证)、列表查询、吊销、下载 .p12/根证书
  - 签发时自动生成 signer_id → 缓存到 Redis (`cert:{signerId}`)
  - 验证: 单元测试覆盖签发/吊销/下载

- [x] **P4.2** 实现 api: 证书管理控制器 + 登录控制器
  - AuthController: POST /api/v1/auth/login
  - CertController: POST /api/v1/cert/issue, GET /api/v1/cert/list, POST /api/v1/cert/revoke, GET /api/v1/cert/download
  - 验证: REST API 联调通过

- [x] **P5.1** 实现 infra/storage: 文件存储适配层
  - FileStorageService 接口: upload(InputStream, fileName) → String url
  - LocalStorageServiceImpl: 本地磁盘 .cert-storage/
  - 策略选择: @ConditionalOnProperty("storage.type")
  - 验证: 上传测试文件可读取

- [x] **P5.2** 实现云存储实现
  - CosStorageServiceImpl (腾讯云)
  - KodoStorageServiceImpl (七牛云)
  - OssStorageServiceImpl (阿里云)
  - 验证: 配置后自动选择对应实现

- [x] **P6.1** 实现 infra/signing: TSA + PDF 签章引擎
  - TsaClient: RFC 3161 时间戳请求，可配置降级策略
  - PdfSigner: 封装 EU DSS PAdES 签名逻辑
  - CoordinateConverter: 前端像素 → PDF Point 坐标换算
  - 验证: 单元测试验证签章后可被 Adobe Reader 识别

- [x] **P6.2** 实现 service: 签章编排服务
  - SigningServiceImpl: 下载PDF → 校验身份 → 坐标换算 → TSA → PAdES签名 → 上传 → 审计日志
  - 证书过期/吊销/不存在的异常处理
  - 临时文件定时清理 (@Scheduled, 24h)
  - 验证: 端到端签章测试

- [x] **P6.3** 实现 api: 签章控制器
  - PdfSignController: POST /api/v1/pdf/sign
  - 验证: API 返回签名后 PDF URL

- [x] **P7.1** 实现 service: 审计日志
  - AuditLog 实体: signer_id, credit_code, cert_serial, sha256_before, sha256_after, sign_time, ip, tsa_summary
  - AuditServiceImpl: 记录日志 + 分页查询
  - 验证: 签章后审计日志落库

- [x] **P8.1** Vue 3 前端项目初始化
  - Vite + Vue 3 + TypeScript + Ant Design Vue + Vue Router + Pinia
  - 代理配置 → 后端 API
  - 验证: `npm run dev` 正常启动

- [x] **P8.2** 实现登录页 + 证书管理页
  - Login.vue: 管理员登录
  - CertManage.vue: 证书列表(表格) + 签发弹窗(表单) + 吊销按钮 + 下载按钮
  - 验证: 签发、列表、吊销、下载全流程

- [x] **P8.3** 实现 PDF 签章画布页
  - SignCanvas.vue: pdfjs-dist 渲染 → 插入印章 (拖拽/缩放) → 清除 → 确认签章
  - 坐标采集: pageIndex, x, y, width, height (PDF 渲染像素)
  - 验证: 印章拖拽 + 确认签章 → 返回新 PDF URL

- [x] **P8.4** 实现审计日志页
  - AuditLog.vue: 审计日志表格，按时间/signer_id 筛选
  - 验证: 签章后审计日志可见
