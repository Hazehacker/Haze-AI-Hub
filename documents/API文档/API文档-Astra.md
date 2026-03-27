# Astra 知识库 API 文档

> - **版本**: v1.0.0
> - **更新日期**: 2026-03-27
> - **基于设计文档**: `Astra设计.md` / `数据库设计.md`

---

[toc]

---

## 1. 概述

Astra 是基于 RAG 的知识库问答系统，核心功能包括：

- 知识库创建与管理
- 多格式文档上传与解析（PDF/Word/图片/音频/XMind）
- 基于向量检索的智能问答
- 会话管理与上下文记忆

### 1.1 技术架构

| 组件 | 技术选型 |
|------|----------|
| 向量数据库 | pgvector (PostgreSQL 扩展) |
| Embedding 模型 | DashScope text-embedding-v3 (1024维) |
| ReRank 模型 | DashScope bge-reranker-v2-m3 |
| 文件解析 | PDFBox + Tika + 阿里 OCR |
| 消息队列 | Redis Stream |
| 解析进度 | SSE (Server-Sent Events) |

### 1.2 数据流概览

```
用户上传文件
    │
    ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  FileController  │ → │  Media记录   │ → │ Redis Stream │
│   (multipart)    │     │  (PENDING)   │     │   解析队列    │
└─────────────┘     └──────────────┘     └──────┬──────┘
                                                │
                    ┌───────────────────────────┼───────────────────────────┐
                    │                           ▼                           │
                    │                   ┌─────────────┐                     │
                    │                   │   消费者     │                     │
                    │                   │  - 文件解析   │                     │
                    │                   │  - Chunk生成 │                     │
                    │                   │  - 向量存储   │                     │
                    │                   └──────┬──────┘                     │
                    │                          │                            │
                    │         ┌─────────────────┼─────────────────┐           │
                    │         ▼                 ▼                 ▼           │
                    │  ┌────────────┐    ┌────────────┐   ┌────────────┐    │
                    │  │ kb_media   │    │  kb_chunk  │   │    SSE     │    │
                    │  │ (PARSED)   │    │ (pgvector) │   │   进度推送   │    │
                    │  └────────────┘    └────────────┘   └────────────┘    │
                    └──────────────────────────────────────────────────────────┘

用户提问
    │
    ▼
┌─────────────┐     ┌──────────────┐     ┌─────────────┐
│  /astra/chat │ → │ 混合检索      │ → │  ReRank     │
│  (流式返回)   │     │ BM25+向量    │     │  Top-K      │
└─────────────┘     └──────────────┘     └──────┬──────┘
                                                │
                    ┌───────────────────────────┘
                    ▼
            ┌─────────────┐
            │  LLM 流式生成 │
            └─────────────┘
```

---

## 2. 基础信息

### 2.1 Base URL

```
/api/v1/astra
```

### 2.2 认证

> 所有接口需要登录态，JWT Token 放在 `Authorization: Bearer <token>` header 中。

### 2.3 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { }
}
```

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 2.4 分页响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [ ],
    "page": 0,
    "size": 20,
    "totalElements": 100,
    "totalPages": 5
  }
}
```

---

## 3. 知识库管理 (Library)

### 3.1 创建知识库

**POST** `/api/v1/astra/libraries`

**Request Body:**

```json
{
  "name": "我的知识库",
  "description": "存放工作文档",
  "type": "personal",
  "coverImage": "https://oss.example.com/covers/default.png"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 是 | 知识库名称，最大32字符 |
| description | string | 否 | 知识库描述，最大255字符 |
| type | string | 是 | `personal` / `team` |
| coverImage | string | 否 | 封面图片URL |

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "我的知识库",
    "description": "存放工作文档",
    "type": "personal",
    "ownerId": 100,
    "isTop": false,
    "coverImage": "https://oss.example.com/covers/default.png",
    "createdAt": "2026-03-27T10:00:00Z",
    "updatedAt": "2026-03-27T10:00:00Z"
  }
}
```

---

### 3.2 获取知识库列表

**GET** `/api/v1/astra/libraries`

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认0 |
| size | int | 否 | 每页大小，默认20 |
| keyword | string | 否 | 搜索关键字（匹配name/description） |

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "name": "我的知识库",
        "description": "存放工作文档",
        "type": "personal",
        "ownerId": 100,
        "isTop": false,
        "coverImage": "https://oss.example.com/covers/default.png",
        "mediaCount": 5,
        "chunkCount": 120,
        "createdAt": "2026-03-27T10:00:00Z",
        "updatedAt": "2026-03-27T10:00:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

> `mediaCount` 和 `chunkCount` 为关联统计，需联表查询或缓存。

---

### 3.3 获取知识库详情

**GET** `/api/v1/astra/libraries/{id}`

**Path Parameters:**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 知识库ID |

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "name": "我的知识库",
    "description": "存放工作文档",
    "type": "personal",
    "ownerId": 100,
    "isTop": false,
    "coverImage": "https://oss.example.com/covers/default.png",
    "mediaCount": 5,
    "chunkCount": 120,
    "createdAt": "2026-03-27T10:00:00Z",
    "updatedAt": "2026-03-27T10:00:00Z"
  }
}
```

---

### 3.4 更新知识库

**PUT** `/api/v1/astra/libraries/{id}`

**Request Body:**

```json
{
  "name": "更新后的名称",
  "description": "更新后的描述",
  "coverImage": "https://oss.example.com/covers/new.png"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| name | string | 否 | 知识库名称 |
| description | string | 否 | 知识库描述 |
| coverImage | string | 否 | 封面图片URL |

**Response:** 返回更新后的完整 LibraryResponse。

---

### 3.5 删除知识库

**DELETE** `/api/v1/astra/libraries/{id}`

> **注意**: 硬删除，需级联清理：
> 1. 删除该知识库下所有 Media 记录
> 2. 删除所有关联的 Chunk 记录（pgvector）
> 3. 删除 OSS 上的原始文件

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

### 3.6 置顶/取消置顶

**PUT** `/api/v1/astra/libraries/{id}/toggle-top`

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "isTop": true
  }
}
```

---

## 4. 媒体文件管理 (Media)

### 4.1 上传文件

**POST** `/api/v1/astra/media`

> **Content-Type**: `multipart/form-data`

**Form Data:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | 上传的文件，最大100MB |
| libraryId | long | 是 | 所属知识库ID |

**处理流程:**

```
1. 校验文件
   ├── MIME类型白名单校验
   ├── 文件大小校验 (≤100MB)
   └── SHA256 完整性校验（防重复上传）

2. 上传到 OSS
   └── 路径格式: astra/{libraryId}/{sha256}.{ext}

3. 创建 Media 记录
   └── status = PENDING

4. 发送解析消息到 Redis Stream
   └── XADD astra:parse:queue {mediaId, libraryId, fileType, ossKey, retryCount: 0}

5. 返回 MediaResponse
```

**支持的文件格式:**

| 类型 | MIME | 说明 |
|------|------|------|
| PDF | `application/pdf` | PDF文档 |
| Word | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` | .docx |
| 图片 | `image/jpeg`, `image/png`, `image/gif`, `image/webp` | 走OCR解析 |
| 音频 | `audio/mpeg`, `audio/wav`, `audio/mp3` | 语音转写 |
| XMind | `application/x-xmind` | 思维导图 |

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "libraryId": 1,
    "fileName": "产品需求文档.pdf",
    "mimeType": "application/pdf",
    "fileSize": 1048576,
    "storagePath": "astra/1/abc123.pdf",
    "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "status": "PENDING",
    "totalChunks": 0,
    "parsedChunks": 0,
    "errorMessage": null,
    "createdAt": "2026-03-27T10:00:00Z",
    "updatedAt": "2026-03-27T10:00:00Z"
  }
}
```

---

### 4.2 获取知识库下的文件列表

**GET** `/api/v1/astra/libraries/{libraryId}/media`

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认0 |
| size | int | 否 | 每页大小，默认20 |
| status | string | 否 | 筛选状态：`PENDING` / `PARSING` / `PARSED` / `FAILED` |

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "libraryId": 1,
        "fileName": "产品需求文档.pdf",
        "mimeType": "application/pdf",
        "fileSize": 1048576,
        "storagePath": "astra/1/abc123.pdf",
        "sha256": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "status": "PARSED",
        "totalChunks": 50,
        "parsedChunks": 50,
        "errorMessage": null,
        "createdAt": "2026-03-27T10:00:00Z",
        "updatedAt": "2026-03-27T10:05:00Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### 4.3 获取文件详情

**GET** `/api/v1/astra/media/{id}`

**Response:** 返回完整的 MediaResponse。

---

### 4.4 获取文件解析进度

**GET** `/api/v1/astra/media/{id}/status`

> 简化版，用于前端轮询场景（与 SSE 互补）

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "status": "PARSING",
    "totalChunks": 50,
    "parsedChunks": 25,
    "percent": 50,
    "errorMessage": null
  }
}
```

| status | 说明 |
|--------|------|
| PENDING | 等待解析 |
| PARSING | 解析中 |
| PARSED | 解析完成 |
| FAILED | 解析失败 |

---

### 4.5 删除文件

**DELETE** `/api/v1/astra/media/{id}`

> 级联清理：
> 1. 删除 kb_media 记录
> 2. 删除 kb_chunk 记录
> 3. 删除 OSS 文件

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 5. 解析进度推送 (SSE)

### 5.1 订阅解析进度

**GET** `/api/v1/astra/media/{mediaId}/stream`

> **注意**: 这是 SSE (Server-Sent Events) 端点，不是普通 REST 接口。

**Response Content-Type:** `text/event-stream`

**连接建立:**

```javascript
const eventSource = new EventSource(`/api/v1/astra/media/${mediaId}/stream`);
```

**SSE 事件格式:**

```
event: progress
data: {"mediaId":123,"status":"PARSING","totalChunks":50,"parsedChunks":12,"percent":24}

event: complete
data: {"mediaId":123,"status":"PARSED","totalChunks":50,"parsedChunks":50}

event: error
data: {"mediaId":123,"status":"FAILED","error":"文件格式不支持或文件损坏"}
```

| 事件类型 | 触发时机 | 关键字段 |
|----------|----------|----------|
| `progress` | 每解析完一个 Chunk 时 | `parsedChunks` / `totalChunks` / `percent` |
| `complete` | 解析全部完成 | `status=PARSED` |
| `error` | 解析失败 | `status=FAILED` + `error` 原因 |

**前端示例:**

```javascript
const eventSource = new EventSource(`/api/v1/astra/media/${mediaId}/stream`);

eventSource.addEventListener('progress', (e) => {
  const data = JSON.parse(e.data);
  updateProgressBar(data.percent);
  updateStatus(data.status);
});

eventSource.addEventListener('complete', (e) => {
  showSuccess('解析完成');
  eventSource.close();
});

eventSource.addEventListener('error', (e) => {
  const data = JSON.parse(e.data);
  showError(data.error);
  eventSource.close();
});

// 连接超时兜底
setTimeout(() => eventSource.close(), 5 * 60 * 1000);
```

**注意事项:**

- SSE 是**单工**通道，只支持服务端推送
- 浏览器对单域名 SSE 连接数有限制（6个）
- 网络断开时浏览器不会自动重连，前端需自行实现重连逻辑

---

## 6. 知识库问答 (RAG Chat)

### 6.1 发送问题

**POST** `/api/v1/astra/chat`

> 流式响应接口，使用 SSE 推送 chunks。

**Request Body:**

```json
{
  "libraryId": 1,
  "sessionId": null,
  "prompt": "这份文档的主要内容包括哪些？"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| libraryId | long | 是 | 知识库ID |
| sessionId | long | 否 | 会话ID，null表示新建会话 |
| prompt | string | 是 | 用户问题 |

**Response Content-Type:** `text/event-stream`

**Response Event Types:**

| 事件类型 | 说明 | data 示例 |
|----------|------|-----------|
| `session-created` | 新建会话时触发 | `{"sessionId": 123}` |
| `thinking` | AI 思考中（ReRank结果） | `"正在检索相关文档..."` |
| `answer` | AI 回答片段 | `"根据文档内容，..."` |
| `complete` | 回答完成 | `{"messageId": 456}` |

**完整响应流:**

```
event: session-created
data: {"sessionId":123}

event: thinking
data: "正在检索相关文档..."

event: thinking
data: "找到5个相关片段，进行重排序..."

event: answer
data: "根据这份文档，主要内容包括："

event: answer
data: "1. 项目概述..."

event: answer
data: "2. 技术架构..."

event: complete
data: {"messageId":456}
```

**RAG 检索流程:**

```
1. query embedding (text-embedding-v3, 1024维)
         │
         ▼
2. 混合检索
   ├── BM25 关键词检索 → Top 50
   └── 向量相似度检索 → Top 50
         │
         ▼
3. RRF 合并去重 → 80-100 候选
         │
         ▼
4. 后过滤
   ├── 内容去重
   ├── 长度过滤 (< 100 tokens 丢弃)
   └── 质量打分 (< 0.3 丢弃)
         │
         ▼
5. ReRank (bge-reranker-v2-m3) → Top 10
         │
         ▼
6. 构建 Prompt + LLM 流式生成
```

**Prompt 模板:**

```markdown
# 知识库问答 Prompt

## 系统 Prompt
你是一个文档问答助手。请根据以下参考内容回答用户问题。
如果参考内容中没有相关信息，请如实告知，不要编造。

## 用户问题
{prompt}

## 参考内容
[文档A-第3页] 这是第一段相关内容...
[文档B-第5页] 这是第二段相关内容...
...

## 回答要求
1. 引用时标注来源
2. 不知道的内容明确说明
3. 回答简洁有据
```

---

### 6.2 获取历史消息

**GET** `/api/v1/astra/sessions/{sessionId}/messages`

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认0 |
| size | int | 否 | 每页大小，默认20 |

**Response:**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [
      {
        "id": 1,
        "sessionId": 123,
        "role": "U",
        "content": "这份文档的主要内容包括哪些？",
        "metadataJson": null,
        "createdAt": "2026-03-27T10:00:00Z"
      },
      {
        "id": 2,
        "sessionId": 123,
        "role": "A",
        "content": "根据这份文档，主要内容包括：\n1. 项目概述\n2. 技术架构...",
        "metadataJson": {
          "model": "qwen-max",
          "chunksUsed": 5,
          "tokens": 1234
        },
        "createdAt": "2026-03-27T10:00:05Z"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

| role | 说明 |
|------|------|
| U | 用户 (User) |
| A | AI (Assistant) |
| system | 系统消息 |

---

## 7. 会话管理

> Astra 复用现有 `chat_session` 表，通过 `type = 'astra'` 区分。

### 7.1 获取 Astra 会话列表

**GET** `/api/v1/ai/session/list?type=astra`

> 复用现有 SessionController，详见 `API文档-Chat.md`

**Query Parameters:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| type | string | 是 | 固定传 `astra` |
| page | int | 否 | 页码 |
| size | int | 否 | 每页大小 |

---

### 7.2 删除会话

**DELETE** `/api/v1/ai/session/{id}`

> 复用现有接口

---

## 8. Redis Stream 消息队列设计

### 8.1 队列结构

```
astra:parse:queue        -- 主解析队列
astra:parse:dlq          -- 死信队列（处理失败后人工/定时处理）
astra-parse-group        -- 消费者组名
```

### 8.2 消息结构

| 字段 | 类型 | 说明 |
|------|------|------|
| mediaId | Long | 待解析的媒体文件ID |
| libraryId | Long | 所属知识库ID |
| fileType | String | 文件类型（PDF/WORD/IMAGE...） |
| ossKey | String | OSS 文件路径 |
| retryCount | Integer | 已重试次数，初始 0 |
| createdAt | Long | 入队时间戳（毫秒） |

**消息发送:**

```java
XADD astra:parse:queue '*'
  mediaId 123
  libraryId 1
  fileType PDF
  ossKey astra/1/abc123.pdf
  retryCount 0
  createdAt 1711520000000
```

### 8.3 消费者设计

**并行度:** `min(文件并发数, CPU核心数)`，建议 2-4 个消费者

**阻塞读取:**

```
XREADGROUP GROUP astra-parse-group <consumerName> BLOCK 5000 COUNT 1
  - BLOCK 5000：无消息时最多阻塞 5 秒
  - COUNT 1：每次取 1 条，保证公平分发
```

**处理流程:**

```
┌─────────────────────────────────────────────────────────┐
│                  消费者主循环                            │
│                                                         │
│  XREADGROUP BLOCK 5000ms                               │
│         │                                               │
│         ▼                                               │
│   ┌───────────┐                                         │
│   │ 有消息？   │──否── 继续 BLOCK 等待新消息              │
│   └─────┬─────┘                                         │
│         │是                                             │
│         ▼                                               │
│   ┌───────────┐                                         │
│   │ 解析文件   │                                         │
│   │ 生成Chunk │                                         │
│   │ SSE推送   │                                         │
│   │ 存入向量库 │                                         │
│   └─────┬─────┘                                         │
│         │                                               │
│         ├──成功───→ XACK ──→ 继续取下一条                │
│         │                                               │
│         └──失败                                         │
│                │                                         │
│                ▼                                         │
│         retryCount < 3？                                │
│            │                                            │
│       是──→ retryCount++                               │
│                │                                        │
│                ├── XADD 同一消息回主队列（带更新后的retryCount）│
│                └── sleep(5秒) ──→ 继续取下一条           │
│                                                         │
│           否──→ XADD 到 DLQ（带错误原因）──→ XACK 原消息 │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 8.4 重试与死信

| 条件 | 动作 |
|------|------|
| 解析失败，`retryCount < 3` | 消息**重新入队**（delay 5秒），`retryCount++` |
| 解析失败，`retryCount >= 3` | 消息移入 `astra:parse:dlq`，`media.status = FAILED` |
| 系统宕机（未 ACK） | Redis Stream 保留未 ACK 消息，消费者重启后自动继续 |

### 8.5 状态同步

Media 状态流转：

```
PENDING（入队） → PARSING（被消费者取出） → PARSED（成功） | FAILED（超过重试上限）
```

消费者处理开始时更新 `media.status = PARSING`，处理完成后更新为 `PARSED` 或 `FAILED`。

---

## 9. 错误码

| 错误码 | HTTP Status | 说明 |
|--------|-------------|------|
| ASTRA_001 | 404 | 知识库不存在 |
| ASTRA_002 | 404 | 媒体文件不存在 |
| ASTRA_003 | 400 | 不支持的文件格式 |
| ASTRA_004 | 413 | 文件大小超出限制 |
| ASTRA_005 | 400 | SHA256 校验失败 |
| ASTRA_006 | 500 | 解析失败（已存入DLQ） |
| ASTRA_007 | 404 | 会话不存在 |
| ASTRA_008 | 400 | 知识库为空，无法问答 |

---

## 10. 附录

### 10.1 文件大小限制

| 文件类型 | 最大大小 |
|----------|----------|
| PDF | 100MB |
| Word | 50MB |
| 图片 | 10MB |
| 音频 | 100MB |
| XMind | 10MB |

### 10.2 Chunk 分块策略

| 内容类型 | chunk_size | 说明 |
|----------|------------|------|
| 短文档（<1500字） | 不拆分，整篇作为1个chunk | 直接利用完整 context |
| 中文档（1500-5000字） | 1000-1200 tokens | 平衡信息密度与召回粒度 |
| 长文档/论文（>5000字） | 1200-1500 tokens | 充分利用 Embedding 上限 |

**Overlap 策略:**
- 重叠比例：`chunk_size 的 20%`，最大 100 tokens
- 短文本（<500 tokens）：**不重叠**
- 代码为主：重叠降至 **10%**

### 10.3 向量检索参数

| 参数 | 值 |
|------|------|
| Embedding 模型 | text-embedding-v3 |
| 向量维度 | 1024 |
| 距离度量 | cosine（余弦距离） |
| 索引算法 | HNSW |
| 检索 Top-K | 50（混合检索各50，RRF合并后80-100） |
| ReRank 后 Top-K | 10 |

---

## 11. 版本历史

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| v1.0.0 | 2026-03-27 | 初始版本 |
