# 认证与权限管理

## 数据表与权限规则

认证模块使用以下数据表，与员工表 `employees` 相互独立：

- `auth_roles`：角色编码（主键）、显示名称和启用状态。
- `auth_users`：自增 ID、唯一用户名、加盐密码哈希、角色编码（外键）、启用状态和 Token 版本。
- `auth_role_permission_profiles`：角色权限配置版本号。
- `auth_role_permissions`：角色拥有的页面或接口权限编码。

每个用户通过 `auth_users.role_code` 对应一个角色，权限只绑定到角色，不存在用户覆盖权限。
所有 `ADMIN` 用户均拥有全部已登记权限，该内置角色不可修改或停用。
全新安装时，`READER` 初始化为业务页面和 GET/HEAD 业务接口权限，之后由管理员统一调整。
管理员可以新建普通角色，为不同岗位配置不同的访问范围；新角色初始权限为空。
角色的权限集合为空时，所有可配置访问均被拒绝，不会恢复默认权限。
管理权限要求调用者的角色编码为 `ADMIN`，不能授予任何普通角色。
所有已认证角色都可以访问自己的账号资料并退出登录。
角色必须存在且处于启用状态；未知或停用的角色不能通过认证。
当前未实现部门级、记录级或字段级的数据隔离。

公开入口包括 `POST /api/auth/login`、`GET /api/health`，以及文档页面和静态资源。
其余现有 HR 业务接口和演示接口均需要 Bearer Token。
CORS 默认只允许 `localhost` 与 `127.0.0.1`，跨域部署需在 `application.yaml` 的 `cors.allowedHosts` 中显式列出域名。

## 首次启动

JWT 签名密钥和可选的初始管理员写在 `src/main/resources/application.yaml` 的 `auth` 段：

该文件仅保存在本机并被 Git 忽略；首次克隆时使用 `application.example.yaml` 创建，再填写真实参数。示例中不提供可直接使用的签名密钥或密码。

- `auth.jwtSecret`：至少 32 字节，用来签发和校验登录 Token
- `auth.adminUsername` / `auth.adminPassword`：同时填写才会在库中还不存在该用户名时创建管理员；已有账号不会被覆盖

在 IDEA 中以 **Debug** 运行配置 `ApplicationKt`（工作目录指向 `backend/`），或临时执行 `./gradlew run`。

服务重启前后应保持 `jwtSecret` 不变，多实例部署时也应使用相同密钥。改密钥会使之前签发的全部 Token 失效，需要重新登录。

服务开始接收请求前，会创建缺失的认证表，并插入两种内置角色，不覆盖已有记录。
初始化需要目标数据库的建表、索引、外键及数据读写权限。
已有 HR 业务表不会被修改。

两个管理员配置项必须同时提供，或者同时省略。
仅当指定用户名不存在时，才会插入初始管理员账号。
重启不会重置已有账号的密码、启用状态或角色。
如果改用另一个不存在的用户名，会创建新的管理员。

对于没有 DDL 权限的部署环境，可以先用具备权限的数据库账号完成初始化，
再设置 `auth.initializeSchema: false`，并切换到权限受限的运行账号。
升级到角色授权时，需要使用具备 DDL 权限的账号初始化一次，以创建两张角色权限表。
首次初始化普通角色时，如存在旧账号级权限，按该角色所有成员原有权限的交集迁移，避免扩大权限。
没有旧账号级限制的角色使用初始默认值；没有预设的自定义角色初始为空。
初始化只执行一次，不会在重启后覆盖管理员保存的角色权限。迁移时会撤销现有账号 Token。
旧 `auth_user_permission_profiles` 和 `auth_user_permissions` 表保留供核对，只在首次迁移时读取，不再参与请求鉴权，也不自动删除历史数据。
该开关不会修改已有表的列结构；后续涉及列结构的变更需要显式迁移。
正常运行时不应继续携带管理员初始化环境变量。

## 登录限流

`POST /api/auth/login` 连续失败会触发限流，同时按来源地址和账号计数（默认 300 秒内分别 30 次与 8 次，见 `auth.loginRateLimit`）。
超限时返回 429 与 `Retry-After` 头，错误码为 `rate_limited`；登录成功后只清账号维度的计数。
计数保存在进程内存里，只保护单个实例；多实例部署或对外暴露时仍需在网关或反向代理上限流。

## 错误码

失败响应除 `code`、`message` 外还带稳定的 `error` 字段，前端按它选择提示文案，因此后端可以自由改写 `message` 的措辞。
常用错误码：`validation_failed`(400)、`unauthorized`(401)、`invalid_credentials`(401 登录失败)、`forbidden`(403)、
`admin_only`(403/400 管理权限)、`role_protected`、`admin_account_protected`、`self_demotion`、`not_found`(404)、
`conflict`(409)、`revision_conflict`(409 权限版本过期)、`username_taken`、`unknown_permission`、
`unsupported_media_type`(415)、`rate_limited`(429)、`internal_error`(500)、`dependency_unavailable`(503)。

500 与数据库约束冲突只返回通用文案（`internal error` / `resource conflict`），具体异常只写日志，不返回给调用方。
`/api` 下的响应统一带 `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY` 和 `Referrer-Policy: no-referrer`。

## 接口调用流程

1. 调用 `POST /api/auth/login`，提交 JSON：
   `{"username":"admin","password":"your-initial-admin-password"}`。
   将密码示例值替换为实际初始密码。响应采用 `{code, message, data}` 格式，JWT 位于 `data.accessToken`。
2. 调用受保护接口时，携带 `Authorization: Bearer <accessToken>`。
   Swagger 和 Knife4j 会根据 `/v3/api-docs` 显示 Authorize 授权入口。
3. 调用 `POST /api/auth/users` 创建默认只读账号，提交 JSON：
   `{"username":"reader","password":"a-unique-strong-password","roleCode":"READER"}`。
   密码示例值应替换为独立设置的强密码。
4. 使用管理员账号调用 `GET /api/auth/users?limit=50&offset=0` 和 `GET /api/auth/roles`，查询用户及角色。
5. `PUT /api/auth/users/{id}` 接收 `roleCode`、`enabled` 和/或 `password`。
   修改后，该账号的全部 Token 会被撤销。管理员不能停用自己或降低自己的角色权限。
   项目没有公开注册接口。用户只能分配已启用的角色，不能提交独立权限。
6. `GET /api/auth/me` 返回当前账号信息；`POST /api/auth/logout` 撤销该账号在所有设备上的 Token。
   退出后需要重新登录以获取新 Token。

## 页面与接口权限

`ADMIN` 用户在前端“角色权限”页面创建或选择普通角色，通过盾牌按钮勾选页面和接口权限。
在“用户管理”中将账号分配给角色。同一角色下所有用户获得完全相同的有效权限。
保存角色权限会更新数据库，并撤销该角色所有用户已有的 Token；停用角色后重新启用也不会恢复旧 Token。

- `GET /api/auth/permissions`：获取已登记的页面和接口权限目录。
- `POST /api/auth/roles`：创建普通角色，提交 `code` 和 `name`，初始权限为空。
- `PUT /api/auth/roles/{code}`：修改普通角色的名称或启用状态。
- `GET /api/auth/roles/{code}/permissions`：获取角色权限和配置版本号。
- `PUT /api/auth/roles/{code}/permissions`：在事务中完整替换角色权限。
- `DELETE /api/auth/users/{id}`：删除账号。内置 `admin` 账号返回 403（`admin_account_protected`），
  删除当前登录账号返回 400（`self_deletion`），账号不存在返回 404。删除后该账号的 Token 立即失效。
- `DELETE /api/auth/roles/{code}`：删除角色及其权限配置。`ADMIN` 角色返回 403（`role_protected`），
  仍有账号引用时返回 409（`role_in_use`，提示剩余账号数），角色不存在返回 404。

员工的部分更新有两个接口：`PUT /api/employees/{id}` 只接受非空值；`PATCH /api/employees/{id}` 支持显式 `null` 清空可空列，
两者权限分别登记为 `api:PUT:/api/employees/{id}` 与 `api:PATCH:/api/employees/{id}`。
未授予 PATCH 权限的角色仍可编辑员工，但不能清空字段。

这些接口只允许 `ADMIN` 角色调用。旧的账号级权限 GET/PUT 接口已移除。
用户名为 `admin` 的内置账号受保护，不能被删除、移除权限、降级或停用，其他管理员也不能重置其密码；
任何管理员都不能删除当前登录的账号。`ADMIN` 角色同样不能删除或改名，只有先把它下面的账号改到别的角色才能删普通角色。
`ADMIN` 角色本身的名称、启用状态和权限不可修改，避免管理员丢失管理入口。
内置 `admin` 仍可以通过已有的用户更新接口修改自己的密码。

请求示例，其中 `revision` 应使用 GET 接口返回的当前版本号：

```json
{
  "revision": 0,
  "permissions": [
    "page:employees",
    "api:GET:/api/employees",
    "api:PUT:/api/employees/{id}"
  ]
}
```

页面权限与接口权限相互独立：允许访问员工页面，不代表自动允许调用员工查询接口；
授予 PUT 权限，也不代表自动授予 POST、DELETE 或按 ID 查询的 GET 权限。
下拉选项所需的岗位、部门等查询接口需要单独授权；没有这些查询权限时，编辑表单允许直接输入编号或编码。
概览是一个包含跨模块统计和员工样本的聚合接口，授权该接口即允许读取其完整响应。
已经登录的用户始终可以访问个人账号、退出登录和无权限提示页面。
登录、健康检查和文档仍然公开，不属于可编辑的权限目录。

提交 `permissions: []` 会清空该角色的全部可配置权限，没有账号级继承或覆盖选项。
提交未知权限编码，或尝试向普通角色授予管理权限，会返回 400。
尝试修改 `ADMIN` 角色或由非管理员修改角色权限，会返回 403。
并发修改时，如果提交的版本号已经过期，会返回 409，不会覆盖较新的配置。
后端根据 HTTP 方法和已登记的路由模板匹配权限；未登记的受保护接口默认拒绝访问。
OpenAPI 接口描述包含 `x-permission` 标记。

用户名会去除首尾空白并统一转换为小写，长度为 3-64 个字符，
仅支持 ASCII 字母、数字、点、下划线和连字符。
密码长度为 8-128 个字符，不能全部为空白。
密码使用 PBKDF2-HMAC-SHA256 哈希算法，配合随机生成的 16 字节盐值，迭代 600,000 次。
接口响应不会包含密码哈希或 Token 版本号。

## Token 验证

JWT 使用 HS256 算法，签名密钥至少为 32 字节。
默认有效期为 3600 秒，`auth.ttlSeconds` 允许设置为 60-86400 秒。
验证内容包括签发者、受众、签名、有效期和必需的声明字段。
当前未提供刷新 Token。

JWT 验证通过后，每次受保护请求都会解析用户、角色及该角色的权限，不读取账号级授权，也不使用 Redis 缓存权限。
进程内有一层短 TTL 缓存（`auth.permissionCacheSeconds`，默认 3 秒，设 0 关闭），key 含 `tokenVersion`：
改密码、改角色、停用账号都会让版本 +1，缓存立即失效；只有"直接在数据库里改数据"最多滞后该 TTL。
用户被停用或删除、角色被停用、Token 版本过期时，请求都会被拒绝。
直接在数据库中调整角色后，下一次请求即可读取到变化；通过管理接口修改时，还会撤销原有 Token。
权限变化发生前已经进入执行阶段的请求，不会被追溯取消。

缺少凭据，或凭据无效、过期、被撤销时返回 401；已认证但权限不足时返回 403。
数据库不可用时不会放行请求。登录失败提示不会透露用户名是否存在。

生产环境应使用 HTTPS 并保护密钥。
内置登录限流只覆盖单实例，对外开放服务前仍应在反向代理或 API 网关配置分布式限流。
如果不希望公开接口规范，应在网关限制文档入口的访问。

## 验证方式

当前没有后端自动化测试。部署到 MySQL 环境时，需在目标环境进行启动和基本接口验证。
