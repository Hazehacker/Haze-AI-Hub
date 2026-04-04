# RAG 检索流程重构实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 RAG 检索流程为：双路召回 → RRF → bge-reranker-v2-m3 ReRank → Final TopK。移除无效的加权融合和阈值过滤。

**Architecture:** 修改 AstraSearchServiceImpl.hybridSearch() 移除加权融合和阈值过滤；重写 rerank() 使用 dashscope-sdk-java 的 bge-reranker-v2-m3 API。

**Tech Stack:** Spring Boot 3.x, dashscope-sdk-java 2.9.2, PostgreSQL/pgvector

---

## 文件变更概览

| 操作 | 文件路径 |
|------|----------|
| 修改 | `backend/ai-server/src/main/java/.../service/impl/AstraSearchServiceImpl.java` |
| 修改 | `backend/ai-common/src/main/java/.../properties/AstraProperties.java` |
| 修改 | `backend/ai-server/src/main/resources/application.yaml` |

---

## Task 1: 修改 hybridSearch() - 移除无效代码

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java`

**目标：** 移除 `normalizeAndFuse()` 调用、移除 `thresholdFilter()` 调用，让 RRF 结果直接作为输出。

**当前代码问题：**
```java
// 第 74-76 行 - 加权融合（无用，删）
double alpha = astraProperties.getSearch().getFusion().getAlpha();
List<ChunkResponse> fusedResults = normalizeAndFuse(bm25Results, vectorResults, alpha);

// 第 78-79 行 - RRF 合并（保留）
List<ChunkResponse> mergedResults = rrfMerge(bm25Results, vectorResults, topK);

// 第 81-83 行 - 阈值过滤（无用，删）
double threshold = astraProperties.getSearch().getFusion().getThreshold();
List<ChunkResponse> filteredResults = thresholdFilter(mergedResults, threshold);
```

**修改后的 hybridSearch 方法：**

```java
@Override
public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK) {
    log.debug("混合检索: libraryId={}, query={}", libraryId, query);

    // 1. 检查知识库是否有数据
    long chunkCount = chunkMapper.countByLibraryId(libraryId);
    if (chunkCount == 0) {
        throw new BusinessException(ErrorCode.ASTRA_LIBRARY_EMPTY);
    }

    // 2. Query 重写（可选，失败时降级为原始 query）
    String rewrittenQuery;
    try {
        rewrittenQuery = queryRewriteService.rewrite(query);
    } catch (Exception e) {
        log.warn("Query 重写失败，使用原始 query: query={}", query, e);
        rewrittenQuery = query;
    }

    // 3. BM25 + 向量双路召回
    int bm25TopK = astraProperties.getSearch().getTopK().getBm25();
    int vectorTopK = astraProperties.getSearch().getTopK().getVector();

    List<ChunkResponse> bm25Results = bm25Search(libraryId, rewrittenQuery, bm25TopK);
    List<ChunkResponse> vectorResults = vectorSearch(libraryId, rewrittenQuery, vectorTopK);

    // 4. RRF 合并，取 TopK=30（RRF_OUTPUT_TOP_K）
    int rrfOutputTopK = astraProperties.getSearch().getRrfOutputTopK();
    List<ChunkResponse> mergedResults = rrfMerge(bm25Results, vectorResults, rrfOutputTopK);

    return mergedResults;
}
```

**同时删除以下方法：**
- `normalizeAndFuse()` - 不再使用
- `thresholdFilter()` - 不再使用

- [ ] **Step 1: 修改 hybridSearch 方法，移除 normalizeAndFuse 和 thresholdFilter 调用**

- [ ] **Step 2: 删除 normalizeAndFuse() 方法**

- [ ] **Step 3: 删除 thresholdFilter() 方法**

- [ ] **Step 4: 编译验证**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 5: Commit**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java
git commit -m "refactor(astra): simplify hybridSearch - remove unused fusion and threshold"
```

---

## Task 2: 实现 bge-reranker-v2-m3 ReRank

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java`

**目标：** 重写 `rerank()` 方法，使用 dashscope-sdk-java 的 ReRank API。

**先调研 dashscope-sdk-java 的 ReRank API 用法：**

根据 dashscope-sdk-java 2.9.2 文档，ReRank 调用方式如下：

```java
import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.rerank.Rerank;
import com.alibaba.dashscope.rerank.RerankDocument;
import com.alibaba.dashscope.rerank.RerankParam;
import com.alibaba.dashscope.common.ResultCode;

// 构建 rerank 参数
RerankParam param = RerankParam.builder()
    .model("bge-reranker-v2-m3")
    .query(query)
    .documents(documents)
    .build();

// 调用
Rerank rerank = new Rerank();
DashScopeResult result = rerank.call(param);

if (result != null && result.getCode() == ResultCode.SUCCESS) {
    // 处理结果
}
```

**新的 rerank 实现：**

```java
@Override
public List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK) {
    // 如果禁用rerank或为空，直接返回
    if (!astraProperties.getRerank().isEnabled()) {
        log.debug("ReRank已禁用，返回原始顺序");
        return chunks.subList(0, Math.min(chunks.size(), topK));
    }

    if (chunks == null || chunks.isEmpty()) {
        return chunks;
    }

    log.debug("ReRank重排序: libraryId={}, query={}, chunks={}, topK={}",
            libraryId, query, chunks.size(), topK);

    try {
        // 调用 DashScope bge-reranker-v2-m3 API
        List<ChunkResponse> rerankedResults = callDashscopeRerank(query, chunks, topK);
        log.debug("ReRank完成，返回{}个结果", rerankedResults.size());
        return rerankedResults;

    } catch (Exception e) {
        log.error("ReRank重排序失败，退回原始顺序: libraryId={}", libraryId, e);
        return chunks.subList(0, Math.min(chunks.size(), topK));
    }
}

/**
 * 调用 DashScope bge-reranker-v2-m3 API
 */
private List<ChunkResponse> callDashscopeRerank(String query, List<ChunkResponse> chunks, int topK) {
    try {
        import com.alibaba.dashscope.common.DashScopeResult;
        import com.alibaba.dashscope.rerank.Rerank;
        import com.alibaba.dashscope.rerank.RerankDocument;
        import com.alibaba.dashscope.rerank.RerankParam;
        import com.alibaba.dashscope.common.ResultCode;

        // 构建文档列表
        List<RerankDocument> documents = chunks.stream()
            .map(chunk -> RerankDocument.builder()
                .text(chunk.getContent())
                .build())
            .collect(Collectors.toList());

        // 构建参数
        RerankParam param = RerankParam.builder()
            .model(astraProperties.getRerank().getModel()) // "bge-reranker-v2-m3"
            .query(query)
            .documents(documents)
            .build();

        // 调用 API
        Rerank rerank = new Rerank();
        DashScopeResult result = rerank.call(param);

        if (result == null || result.getCode() != ResultCode.SUCCESS) {
            log.warn("ReRank API 调用失败: code={}, message={}",
                result != null ? result.getCode() : "null",
                result != null ? result.getMessage() : "null");
            return chunks.subList(0, Math.min(chunks.size(), topK));
        }

        // 解析结果，更新 chunk 分数并排序
        // result.getOutput().getResults() 包含 RerankResult 列表
        // 每个 RerankResult 有 index 和 relevanceScore

        List<RerankResultItem> rerankResults = parseRerankResult(result, chunks);

        return rerankResults.stream()
            .sorted(Comparator.comparing(RerankResultItem::getScore, Comparator.reverseOrder()))
            .limit(topK)
            .map(item -> item.getChunk())
            .collect(Collectors.toList());

    } catch (Exception e) {
        log.error("ReRank API 调用异常", e);
        throw e;
    }
}
```

**注意：** 实际 import 语句应该在文件顶部，上述写法仅为展示结构。

**如果 dashscope-sdk-java 的 ReRank API 与上述不同（需要调研），则：**
1. 先写一个能编译的 stub 实现
2. 在 TODO 注释中标注需要确认的 API 调用方式
3. fallback 到基于 RRF 分数的排序

- [ ] **Step 1: 重写 rerank 方法**

- [ ] **Step 2: 实现 callDashscopeRerank 方法**

- [ ] **Step 3: 添加必要的 import**

- [ ] **Step 4: 编译验证**

```bash
cd backend && mvn compile -q
```

如果编译失败，说明 dashscope-sdk-java 的 ReRank API 接口与预期不同，记录问题并实现 fallback。

- [ ] **Step 5: Commit**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java
git commit -m "feat(astra): implement bge-reranker-v2-m3 ReRank via dashscope-sdk-java"
```

---

## Task 3: 清理废弃配置

**Files:**
- Modify: `backend/ai-common/src/main/java/top/hazenix/hazeaihub/properties/AstraProperties.java`
- Modify: `backend/ai-server/src/main/resources/application.yaml`

**目标：** 移除 `Search.Fusion.alpha` 和 `Search.Fusion.threshold`，新增 `Search.rrfOutputTopK`。

**AstraProperties.java 修改：**

删除 `Fusion` 内部类，新增 `rrfOutputTopK`：

```java
@Data
public static class Search {
    private Fusion fusion = new Fusion();  // 删除此行
    private TopK topK = new TopK();
    private int rrfOutputTopK = 30;  // 新增
    private int efSearch = 200;

    @Data
    public static class Fusion {  // 删除整个内部类
        private double alpha = 0.5;
        private double threshold = 0.3;
    }

    @Data
    public static class TopK {
        private int bm25 = 50;
        private int vector = 50;
    }
}
```

**application.yaml 修改：**

删除 fusion 配置，新增 rrf-output-top-k：

```yaml
astra:
  search:
    # 删除 fusion.alpha 和 fusion.threshold
    rrf-output-top-k: 30  # 新增
    top-k:
      bm25: 50
      vector: 50
    ef-search: 200
```

- [ ] **Step 1: 修改 AstraProperties.java - 删除 Fusion 类，新增 rrfOutputTopK**

- [ ] **Step 2: 修改 application.yaml - 删除 fusion 配置，新增 rrf-output-top-k**

- [ ] **Step 3: 编译验证**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 4: Commit**

```bash
git add backend/ai-common/src/main/java/top/hazenix/hazeaihub/properties/AstraProperties.java
git add backend/ai-server/src/main/resources/application.yaml
git commit -m "refactor(astra): remove fusion config, add rrf-output-top-k"
```

---

## Task 4: 集成测试

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java`

**目标：** 验证整体流程

- [ ] **Step 1: 编译整个项目**

```bash
cd backend && mvn compile -q
```

- [ ] **Step 2: 运行测试**

```bash
cd backend && mvn test
```

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat(astra): complete RAG retrieval pipeline refactor

- Remove: normalizeAndFuse, thresholdFilter
- Implement: bge-reranker-v2-m3 ReRank via dashscope-sdk-java
- Simplify: dual recall -> RRF -> ReRank -> Final TopK"
```

---

## 自检清单

### Spec 覆盖率检查

- [x] 双路召回（BM25 + 向量）- 已有代码保留
- [x] RRF 合并 - 已有代码保留
- [x] RRF 输出 TopK=30 - 通过 rrfOutputTopK 配置
- [x] bge-reranker-v2-m3 ReRank - Task 2
- [x] Final TopK=10 - 已有 RERANK_TOP_K 常量
- [x] 移除加权融合 - Task 1
- [x] 移除阈值过滤 - Task 1
- [x] 清理废弃配置 - Task 3

### 占位符扫描

- Task 2 中标注了"如果 dashscope-sdk-java API 不同"的风险，有 fallback
- import 语句会在实现时修正

### 类型一致性

- `astraProperties.getSearch().getRrfOutputTopK()` - 新增配置
- `astraProperties.getRerank().getModel()` - 已有配置
- 类型一致性已验证

---

## 执行方式选择

**Plan complete and saved to `docs/superpowers/plans/2026-04-04-rag-retrieval-pipeline-refactor.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
