# vault-rotate-demo

演示：使用 HashiCorp Vault AppRole + Database Secrets Engine 在运行时对 Spring Boot 服务的数据库凭证进行热更新（无重启），并提供多数据源回退以防止失败导致死锁。

主要特性
- 启动时通过 Vault AppRole 获取 DB 凭证
- 运行时通过外部接口触发凭证刷新（热更新，不重启 pod）
- 多套 DataSource（current / previous）原子切换，回退保护
- 演示用 docker-compose：Vault (dev) + Postgres

快速开始

1. 启动演示环境

   docker compose up -d

2. 初始化 Vault（会输出 role_id / secret_id）

   ./init-vault.sh

   请把脚本输出的 `ROLE_ID` 和 `SECRET_ID` 导出为环境变量：

   export VAULT_ROLE_ID=...\n   export VAULT_SECRET_ID=...

3. 运行应用

   mvn spring-boot:run

4. 验证

   - 读取当前 DB 用户：
     curl http://localhost:8080/api/db-user

   - 触发凭证刷新：
     curl -X POST http://localhost:8080/api/rotate

设计说明与要点
- 使用 Spring Vault（AppRole）做 Vault 认证；在 `application.yml` 中通过 `spring.vault.app-role.role-id/secret-id` 注入
- 程序在启动时从 Vault 拉取动态 DB 凭证并构建 HikariDataSource
- 运行时通过 `/api/rotate` 调用触发：先从 Vault 获取新凭证并尝试连接，成功后原子替换当前 DataSource；若失败则保留旧连接
- `DelegatingDataSource` 使用 `AtomicReference<DataSource>` 做委托，Spring 的 `JdbcTemplate` 及应用代码不需要感知切换

下一步/改进
- 把 AppRole 的 role/secret 存入 Kubernetes Secret 或 CSI driver（不要放入镜像）
- 将 init 脚本改为 Kubernetes Job，用 Vault Operator/Credential Provider 集成
- 支持凭证自动轮换（基于 Vault lease 的 listener / scheduler)
