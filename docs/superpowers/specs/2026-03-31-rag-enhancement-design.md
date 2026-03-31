# RAG 检索增强设计方案

## 概述

RAG 检索增强采用三阶段渐进式实现：Query 重写 → 阈值过滤 → ReRank 重排序。分阶段交付，逐步提升答案准确率。

## 整体流程

```
用户 Query
    ↓
┌─────────────────────────────────────────┐
│  Phase 1: Query 重写                     │
│  DashScope qwen-turbo 轻量改写            │
│  独立服务 + 配置开关                      │
└─────────────────────────────────────────┘
    ↓ 重写后的 Query
┌─────────────────────────────────────────┐
│  Phase 2: 混合搜索 + 阈值过滤             │
│  BM25 + 向量双路召回                      │
│  归一化后 alpha=0.5 加权                  │
│  阈值过滤 → 保留高分 chunk                │
└─────────────────────────────────────────┘
    ↓ 过滤后的 chunks
┌─────────────────────────────────────────┐
│  Phase 3: ReRank 重排序                   │
│  DashScope bge-reranker-v2-m3           │
│  最终 TopK 输出                           │
└─────────────────────────────────────────┘
    ↓
LLM 生成答案
```

---

## Phase 1: Query 重写

### 服务设计

**接口：** `IQueryRewriteService`

```java
public interface IQueryRewriteService {
    /**
     * 重写查询文本
     * @param query 原始查询
     * @return 重写后的查询
     */
    String rewrite(String query);

    /**
     * 是否启用 Query 重写
     */
    boolean isEnabled();
}
```

### Prompt 设计（Few-shot + 约束）

```
你是一个查询改写助手。请将用户问题简化并规范化为适合知识库检索的标准查询形式。

要求：
1. 去除口语化表达和语气词
2. 保留核心意图和关键术语
3. 保持简洁，不超过原句长度
4. 不添加原句中没有的信息

示例：
输入："这个PDF里面讲了些什么内容啊"
输出："PDF内容摘要"

输入："怎么才能创建实例呢"
输出："如何创建实例"

输入："请问一下关于微服务架构的设计原则都有哪些"
输出："微服务架构设计原则"

输入："{user_query}"
输出：
```

### 模型配置

- 模型：`qwen-turbo`
- Max Tokens：`128`
- Temperature：`0.3`

### 配置项

```yaml
astra:
  query-rewrite:
    enabled: true   # 可开关
    model: qwen-turbo
    max-tokens: 128
    temperature: 0.3
```

---

## Phase 2: 混合搜索 + 阈值过滤

### 评分融合逻辑

1. BM25 分数归一化 → `bm25Norm = bm25Score / maxBm25Score`
2. 向量相似度归一化 → `vectorNorm = vectorScore / maxVectorScore`
3. 融合分 = `alpha × bm25Norm + (1-alpha) × vectorNorm`，alpha=0.5
4. 阈值过滤：`fusionScore >= threshold` → 保留

### ChunkResponse 字段扩展

```java
@Schema(description = "BM25分数")
private Float bm25Score;

@Schema(description = "向量相似度分数")
private Float vectorScore;
```

### 过滤流程

```
hybridSearch(libraryId, query, topK):
  1. Query 重写（可选）
  2. BM25 + 向量双路召回（同时记录分项分数）
  3. 归一化 + 加权融合
  4. 阈值过滤
  5. 返回过滤后的 chunks
```

### 配置项

```yaml
astra:
  search:
    fusion:
      alpha: 0.5        # BM25权重，0.5=平衡
      threshold: 0.3     # 融合分阈值
    top-k:
      bm25: 50
      vector: 50
```

---

## Phase 3: ReRank 重排序

### 执行顺序

先阈值过滤，再 ReRank 排序。

### API 调用

使用 DashScope `bge-reranker-v2-m3` 模型。

### 逻辑

```
rerank(libraryId, query, chunks, topK):
  1. 构造 API 请求（query + chunk contents）
  2. 调用 DashScope bge-reranker-v2-m3
  3. 更新 chunks 的 relevance_score
  4. 按分数排序返回 TopK
  5. 失败时退回原始顺序
```

### 配置项

```yaml
astra:
  rerank:
    enabled: true
    model: bge-reranker-v2-m3
    top-k: 10
```

---

## 文件变更清单

| 操作 | 文件路径 |
|------|----------|
| 新增 | `service/IQueryRewriteService.java` |
| 新增 | `service/impl/QueryRewriteServiceImpl.java` |
| 修改 | `vo/ChunkResponse.java` — 增加 bm25Score, vectorScore 字段 |
| 修改 | `service/impl/AstraSearchServiceImpl.java` — 集成重写 + 阈值过滤 |
| 修改 | `resources/application.yaml` — 增加配置项 |

---

## 实现顺序

1. **Phase 1**: Query 重写（独立服务）
2. **Phase 2**: 阈值过滤（修改 hybridSearch）
3. **Phase 3**: ReRank（改造 rerank 方法）
