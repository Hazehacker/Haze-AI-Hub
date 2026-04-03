# Haze AI Hub

一个全栈式 AI 聊天应用，支持多模态对话、知识库管理和智能客服。

## 功能特性

### 核心功能
- **多模态 AI 对话** - 支持文本、图片、音频等多模态交互
- **SSE 流式响应** - 实时流式返回 AI 回复
- **会话管理** - 支持分组、置顶、软删除
- **模型切换** - 支持多种 AI 模型切换
- **自动标题生成** - 基于首轮对话 AI 生成会话标题
- **文件上传** - 支持图片/音频/视频上传，带 MIME 类型验证和 SHA256 校验

### Astra 知识库
- **PDF 文档解析** - Apache PDFBox + Tika 文本提取
- **向量检索** - 基于 pgvector 的 RAG 知识问答
- **多格式支持** - PDF / Word / 图片 / 音频 / XMind
- **异步解析** - Redis Stream 消息队列实现削峰填谷
- **解析进度推送** - SSE 实时推送解析状态

### 技术架构
- **后端**: Spring Boot 3.x + Spring AI Alibaba
- **前端**: Vue 3 + TypeScript
- **数据库**: PostgreSQL + pgvector
- **AI 集成**: 阿里云 DashScope (通义千问、text-embedding-v3)
- **消息队列**: Redis Stream

## 部分功能预览图

![image-20260403192115425](documents\preview-pic\text_to_pic.jpg)







## 项目结构

```
Haze-AI-Hub/
├── backend/                    # 后端服务
│   ├── ai-common/             # 公共模块（常量、工具类）
│   ├── ai-pojo/              # 实体类、DTO、VO
│   │   └── src/main/java/top/hazenix/hazeaihub/
│   │       ├── entity/       # 实体类
│   │       ├── dto/          # 数据传输对象
│   │       ├── vo/           # 视图对象
│   │       └── handler/      # 类型处理器（JSONB）
│   └── ai-server/             # 主服务模块
│       └── src/main/java/top/hazenix/hazeaihub/
│           ├── controller/   # 控制器（Chat/Session/File/Astra）
│           ├── service/      # 服务接口
│           │   └── impl/     # 服务实现
│           ├── mapper/       # MyBatis Mapper
│           └── config/       # 配置类
├── frontend/                   # 前端应用 (Vue 3 + TypeScript)
├── documents/                  # 设计文档
│   ├── 数据库设计.md          # 数据库表结构
│   ├── 多模态对话系统设计.md  # 对话系统设计
│   ├── Astra设计.md           # 知识库设计
│   └── 前端对接指南.md        # API 对接文档
└── CHANGES.md                  # 更新日志
```

## 快速开始

### 环境要求

- JDK 17+
- Node.js 18+
- PostgreSQL 12+ (需启用 pgvector 扩展)
- Redis 6+

### 后端启动

```bash
cd backend/

# 构建项目
mvn clean package -DskipTests

# 运行应用
mvn spring-boot:run -pl ai-server

# 或直接运行 JAR
java -jar ai-server/target/ai-server-*.jar
```

### 前端启动

```bash
cd frontend/

# 安装依赖
npm install

# 开发模式启动
npm run dev

# 生产构建
npm run build
```

### 环境变量

```bash
# 阿里云 DashScope API Key
DASHSCOPE_API_KEY=your_api_key
```

## 数据库配置

PostgreSQL 需要启用 pgvector 扩展：

```sql
CREATE EXTENSION IF NOT EXISTS vector;

-- 创建数据库
CREATE DATABASE "haze-ai-hub";
```

application.yaml 中的数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/haze-ai-hub
    username: postgres
    password: your_password
```

## API 文档

### 核心接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/ai/chat-with-thinking-text` | POST | 流式对话（含思考过程） |
| `/api/v1/ai/session/create` | POST | 创建会话 |
| `/api/v1/ai/session/list` | GET | 获取会话列表 |
| `/api/v1/ai/session` | PUT | 更新会话 |
| `/api/v1/ai/session` | DELETE | 删除会话 |
| `/api/v1/ai/models` | GET | 获取模型列表 |
| `/api/v1/ai/file/upload` | POST | 文件上传 |

详细 API 文档请参考 [documents/前端对接指南.md](documents/前端对接指南.md)

## 核心设计

### 会话管理

会话由首条消息触发创建，前端不主动创建会话 ID。流程：

1. 用户发送首条消息（sessionId 为空）
2. 后端创建会话并保存消息
3. 流式返回 AI 回复
4. 异步生成会话标题

### Astra 知识库

采用 RAG (Retrieval Augmented Generation) 架构：

```
用户问题 → 向量化 → pgvector 相似度检索 → Top-K Chunks → LLM 生成答案
```

解析流程：

```
文件上传 → Media 元信息创建 → Redis Stream 队列 →
解析服务消费 → PDFBox/Tika 文本提取 → 智能分块 →
Embedding 向量化 → pgvector 存储
```

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.x, Spring AI Alibaba |
| 前端框架 | Vue 3, TypeScript, Vite |
| 数据库 | PostgreSQL, pgvector |
| 缓存/队列 | Redis, Redis Stream |
| AI 模型 | 通义千问, text-embedding-v3 |
| 文档解析 | Apache PDFBox, Apache Tika, Apache POI |
| ORM | MyBatis-Plus |
| 构建工具 | Maven, npm |

## 相关文档

- [后端详细文档](backend/README.md)
- [数据库设计](documents/数据库设计.md)
- [多模态对话设计](documents/多模态对话系统设计.md)
- [Astra 知识库设计](documents/Astra设计.md)
- [前端对接指南](documents/前端对接指南.md)
- [更新日志](CHANGES.md)

## License

MIT
