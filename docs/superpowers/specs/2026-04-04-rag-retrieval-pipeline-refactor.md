# RAG 检索流程重构设计方案

## 背景

当前 RAG 检索流程存在问题：
1. `normalizeAndFuse()` 计算了加权融合分，但结果被丢弃（`rrfMerge` 覆盖了它）
2. QA 对检索未集成到 `hybridSearch()`
3. ReRank 使用 LLM fallback，未调用 bge-reranker-v2-m3

## 目标

简化为：**双路召回 → RRF → ReRank(bge-reranker-v2-m3) → Final TopK**

## 新架构流程

```
用户 Query
    ↓
┌─────────────────────────────────────────┐
│  Phase 1: Query 重写                     │
│  DashScope qwen-turbo 轻量改写            │
└─────────────────────────────────────────┘
    ↓ 重写后的 Query
┌─────────────────────────────────────────┐
│  Phase 2: 双路召回                        │
│  BM25 (topK=50) + 向量检索 (topK=50)    │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  Phase 3: RRF 合并                        │
│  Reciprocal Rank Fusion (k=60)           │
│  输出 TopK=30                            │
└─────────────────────────────────────────┘
    ↓ 30 个 chunks
┌─────────────────────────────────────────┐
│  Phase 4: ReRank 重排序                   │
│  DashScope bge-reranker-v2-m3            │
│  全量 30 个重新打分                       │
└─────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────┐
│  Phase 5: Final TopK                      │
│  取 TopK=10 送入 LLM                     │
└─────────────────────────────────────────┘
```

## 关键参数

| 参数 | 值 | 说明 |
|------|-----|------|
| BM25 topK | 50 | 关键词召回数量 |
| 向量检索 topK | 50 | 向量召回数量 |
| RRF k | 60 | RRF 平滑因子 |
| RRF 输出 topK | 30 | 送入 ReRank 的数量 |
| ReRank 模型 | bge-reranker-v2-m3 | DashScope 重排模型 |
| Final topK | 10 | 送入 LLM 的数量 |

## 代码改动

### 1. AstraSearchServiceImpl.java

**删除：**
- `normalizeAndFuse()` 方法（加权融合，不再使用）
- `thresholdFilter()` 方法（阈值过滤，不再使用）

**修改 `hybridSearch()`：**
```java
public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK) {
    // 1. 检查知识库
    // 2. Query 重写
    // 3. BM25 + 向量双路召回
    // 4. RRF 合并，取 TopK=30
    // 5. 返回 RRF 结果（送入 ReRank）
}
```

**修改 `rerank()`：**
```java
public List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK) {
    // 1. 调用 dashscope-sdk-java bge-reranker-v2-m3 API
    // 2. 获取每个 chunk 的 relevance_score
    // 3. 按分数排序，取 topK
    // 4. 返回结果
}
```

### 2. AstraProperties.java

**删除：**
- `Search.Fusion.alpha`
- `Search.Fusion.threshold`

**保留：**
- `Search.TopK.bm25`
- `Search.TopK.vector`
- `Search.efSearch`

### 3. application.yaml

**删除：**
```yaml
astra:
  search:
    fusion:
      alpha: 0.5
      threshold: 0.3
```

## ReRank API 调用

使用 `dashscope-sdk-java` 2.9.2 的 ReRank API：

```java
import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.rerank.Rerank;
import com.alibaba.dashscope.rerank.RerankDocument;
import com.alibaba.dashscope.rerank.RerankResult;

// 调用方式（伪代码）
Rerank rerank = new Rerank();
List<RerankDocument> documents = chunks.stream()
    .map(c -> RerankDocument.builder().text(c.getContent()).build())
    .collect(Collectors.toList());

RerankResult result = rerank.call(RerankParam.builder()
    .model("bge-reranker-v2-m3")
    .query(query)
    .documents(documents)
    .build());

// result 包含每个 document 的 relevance_score
```

## 不在本次范围内

- QA 对检索（`qaVectorSearch`）- 暂不激活
- 加权融合
- 阈值过滤

## 风险点

1. `dashscope-sdk-java` ReRank API 实际接口需验证
2. 如 SDK 不支持，需改用 HTTP 调用
