# Haze AI Hub - 后端服务

## 📋 项目结构

```
backend/
├── ai-common/          # 公共模块
├── ai-pojo/           # 实体类、DTO、VO
│   └── src/main/java/top/hazenix/hazeaihub/
│       ├── entity/    # 实体类
│       ├── dto/       # 数据传输对象
│       ├── vo/        # 视图对象
│       └── handler/   # 类型处理器
└── ai-server/         # 服务模块
    └── src/main/java/top/hazenix/hazeaihub/
        ├── controller/  # 控制器
        ├── service/     # 服务接口
        │   └── impl/    # 服务实现
        ├── mapper/      # MyBatis Mapper
        └── config/      # 配置类
```

## 🚀 核心功能

### 1. 会话管理

**服务层**：`IChatSessionService` / `ChatSessionServiceImpl`

**功能**：
- 创建会话
- 更新会话（标题、分组）
- 删除会话（软删除）
- 获取会话列表（支持分页、筛选）
- 置顶/取消置顶
- 更新最后活跃时间

**接口**：
- `POST /api/v1/ai/session/create` - 创建会话
- `PUT /api/v1/ai/session` - 更新会话
- `DELETE /api/v1/ai/session` - 删除会话
- `GET /api/v1/ai/session/list` - 获取会话列表
- `PUT /api/v1/ai/session/toggle-top` - 置顶会话

### 2. 消息管理

**服务层**：`IChatMessageService` / `ChatMessageServiceImpl`

**功能**：
- 保存用户消息
- 保存AI消息（支持元数据）
- 获取会话消息列表
- 获取首轮对话（用于生成标题）
- 删除消息（软删除）

### 3. 标题生成

**服务层**：`ITitleGenerationService` / `TitleGenerationServiceImpl`

**功能**：
- 根据对话内容生成标题
- 异步生成并更新会话标题
- 使用 qwen-turbo 快速模型
- 自动限制标题长度

**特点**：
- 异步执行，不阻塞主流程
- 基于首轮对话（用户问题 + AI回答）
- 失败时使用默认标题"新对话"

### 4. AI 对话

**服务层**：`IBailianThinkingService` / `BailianThinkingServiceProImpl`

**功能**：
- 流式调用大模型
- 支持思考过程
- 自动管理会话和消息
- 首条消息自动创建会话
- 流结束后自动保存消息
- 新会话自动生成标题

**流程**：
1. 检测 sessionId 是否为空
2. 如果为空，创建新会话并保存用户消息
3. 如果存在，直接保存用户消息
4. 获取历史消息构建上下文
5. 流式调用大模型
6. 收集思考内容和回答内容
7. 流结束后保存AI消息
8. 更新会话活跃时间
9. 如果是新会话，异步生成标题

## 🔧 配置说明

### 1. 数据库配置

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/haze-ai-hub
    username: postgres
    password: your_password
```

### 2. AI 模型配置

```yaml
spring:
  ai:
    openai:
      api-key: ${DASHSCOPE_API_KEY}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1

ai:
  bailian:
    default:
      model: deepseek-r1  # 默认模型
```

### 3. 异步任务配置

异步任务配置在 `AsyncConfiguration` 中：
- 核心线程数：2
- 最大线程数：5
- 队列容量：100
- 线程名前缀：async-task-

## 📝 使用示例

### 1. 发送首条消息（创建会话）

```java
// 前端调用
POST /api/v1/ai/chat-with-thinking-text
?prompt=帮我写一首春天的诗
&sessionId=
&enableThinking=true

// 后端处理流程
1. 检测到 sessionId 为空
2. 创建新会话（userId从上下文获取）
3. 保存用户消息
4. 流式调用大模型
5. 返回 session-created 事件
6. 流式返回 AI 回复
7. 保存 AI 消息
8. 异步生成标题
```

### 2. 发送后续消息

```java
// 前端调用
POST /api/v1/ai/chat-with-thinking-text
?prompt=再写一首夏天的
&sessionId=123
&enableThinking=true

// 后端处理流程
1. 检测到 sessionId 存在
2. 保存用户消息
3. 获取历史消息（最近10条）
4. 流式调用大模型（带历史上下文）
5. 流式返回 AI 回复
6. 保存 AI 消息
7. 更新会话活跃时间
```

### 3. 获取会话列表

```java
// 前端调用
GET /api/v1/ai/session/list
?userId=1
&type=chat
&page=1
&pageSize=20

// 响应
{
  "code": 200,
  "data": [
    {
      "id": 123,
      "title": "创作春天的诗",
      "type": "chat",
      "isTop": false,
      "lastActiveAt": "2026-01-27 10:30:00",
      "messageCount": 6,
      "lastMessagePreview": "春风拂面，万物复苏..."
    }
  ]
}
```

## 🔍 调试技巧

### 1. 查看日志

```bash
# 查看会话创建日志
grep "创建新会话成功" logs/app.log

# 查看标题生成日志
grep "标题生成" logs/app.log

# 查看消息保存日志
grep "消息保存成功" logs/app.log
```

### 2. 数据库查询

```sql
-- 查看最新创建的会话
SELECT * FROM chat_session 
ORDER BY created_at DESC 
LIMIT 10;

-- 查看会话的消息
SELECT * FROM chat_message 
WHERE session_id = 123 
ORDER BY created_at ASC;

-- 查看包含思考过程的消息
SELECT id, session_id, role, 
       metadata_json->>'thinking_content' as thinking,
       metadata_json->>'model' as model
FROM chat_message 
WHERE metadata_json->>'thinking_content' IS NOT NULL;
```

## 🐛 常见问题

### 1. 标题生成失败

**原因**：
- API Key 无效
- 网络连接问题
- 模型调用失败

**解决**：
- 检查日志中的错误信息
- 标题生成失败不影响主流程
- 会话标题保持为"新对话"

### 2. 会话创建失败

**原因**：
- 用户未登录（BaseContext.getCurrentId() 为空）
- 数据库连接问题

**解决**：
- 确保请求携带有效的 JWT Token
- 检查数据库连接配置

### 3. 消息保存失败

**原因**：
- 会话不存在
- 数据库事务回滚

**解决**：
- 检查 sessionId 是否有效
- 查看数据库日志

## 📚 相关文档

- [多模态对话设计](../documents/多模态对话设计.md)
- [API接口文档](../documents/API接口文档.md)
- [数据库设计](../documents/数据库设计.md)
- [更新日志](../CHANGES.md)

## 🔄 版本历史

### v1.0.0 (2026-01-27)
- 实现完整的会话与消息管理流程
- 支持异步标题生成
- 优化服务层架构
- 完善接口文档

### v0.9.0 (2026-01-25)
- 添加模型切换功能
- 支持多种AI模型

### v0.8.0 (2026-01-24)
- 网络错误处理优化
- Duration序列化修复
