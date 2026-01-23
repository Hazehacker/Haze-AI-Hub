# 修复历史记录 - 2026-01-23

## 问题描述
接口 `api/v1/ai/history/chat/5` 存在两个问题：
1. 不能正确把数据库中的 JSONB 类型数据转成属性值（`metadataJson` 字段为 null）
2. 返回成功之后前端没展示历史会话

## 修复内容

### 后端修复

#### 1. 优化 JsonTypeHandler（`backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/handler/JsonTypeHandler.java`）
- 添加 `@Slf4j` 注解用于日志记录
- 添加 `@MappedJdbcTypes(JdbcType.OTHER)` 注解，明确映射 PostgreSQL 的 JSONB 类型
- 改进 `parseJson` 方法：
  - 使用 `rs.getObject()` 替代 `rs.getString()`，正确处理 PGobject 类型
  - 添加 PGobject 类型判断和处理
  - 使用 TypeReference 提高类型安全性
  - 增强错误日志记录

#### 2. 更新 ChatMessage 实体类（`backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/entity/ChatMessage.java`）
- 在 `@TableField` 注解中添加：
  - `value = "metadata_json"` 明确字段名
  - `jdbcType = JdbcType.OTHER` 指定 JDBC 类型为 OTHER（PostgreSQL JSONB）
- 移除未使用的 `@NotBlank` 导入

#### 3. 更新 MyBatis-Plus 配置（`backend/ai-server/src/main/resources/application.yaml`）
- 添加 `jdbc-type-for-null: NULL` 配置
- 添加 `type-handlers-package: top.hazenix.hazeaihub.handler` 自动扫描类型处理器

### 前端修复

#### 修复 API 调用（`frontend/src/services/api.js`）

**getChatHistory 方法：**
- 修复返回值解析：正确处理 `Result<List<Long>>` 格式
- 从 `result.data` 中提取会话 ID 列表
- 添加 `toString()` 方法确保 ID 转换为字符串后再截取

**getChatMessages 方法：**
- 修复返回值解析：正确处理 `Result<List<ChatMessage>>` 格式
- 从 `result.data` 中提取消息列表
- 正确映射消息字段：
  - `role`: 消息角色
  - `content`: 消息内容
  - `timestamp`: 从 `createdAt` 转换
  - `metadata`: 保留 `metadataJson` 元数据

## 技术要点

### PostgreSQL JSONB 类型处理
1. **写入**：使用 `PGobject` 包装 JSON 字符串，设置类型为 "jsonb"
2. **读取**：
   - 使用 `getObject()` 而非 `getString()` 获取原始对象
   - 判断是否为 `PGobject` 类型并提取值
   - 使用 Jackson ObjectMapper 解析 JSON

### MyBatis TypeHandler 配置
1. **注解方式**：
   - `@MappedTypes`: 指定 Java 类型
   - `@MappedJdbcTypes`: 指定 JDBC 类型（PostgreSQL JSONB 使用 OTHER）
2. **实体类配置**：
   - `@TableField` 注解指定字段名、类型处理器和 JDBC 类型
3. **全局配置**：
   - 在 application.yaml 中配置 type-handlers-package 自动扫描

## 测试验证

### 后端编译
```bash
cd backend
mvn clean compile -DskipTests
```
编译成功，无错误。

### 预期效果
1. 接口返回的 `metadataJson` 字段能正确解析为 Map 对象
2. 前端能正确展示历史会话列表
3. 前端能正确展示会话中的消息历史，包括元数据信息

### 影响范围
修复后，以下页面的历史记录功能都将正常工作：
- **AI聊天** (`AIChat.vue`) - 使用 `type='chat'`
- **ChatPDF** (`ChatPDF.vue`) - 使用 `type='pdf'`
- **智能客服** (`CustomerService.vue`) - 使用 `type='service'`

## 相关文件
- `backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/handler/JsonTypeHandler.java`
- `backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/entity/ChatMessage.java`
- `backend/ai-server/src/main/resources/application.yaml`
- `frontend/src/services/api.js`
