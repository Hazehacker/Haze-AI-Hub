# RAG 检索增强实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Astra 知识库搜索流程中，分三阶段渐进式增强 RAG 检索能力：Query 重写 → 阈值过滤 → ReRank 重排序。

**Architecture:** 新增独立 QueryRewriteService，复用现有 DashScopeChatModel 调用 qwen-turbo；改造 hybridSearch 实现归一化融合 + 阈值过滤；实现 rerank() 调用 DashScope bge-reranker-v2-m3 API。

**Tech Stack:** Spring Boot 3.x, Spring AI Alibaba, DashScope API, PostgreSQL/pgvector

---

## 文件变更概览

| 操作 | 文件路径 |
|------|----------|
| 新增 | `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/IQueryRewriteService.java` |
| 新增 | `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/QueryRewriteServiceImpl.java` |
| 新增 | `backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/AstraProperties.java` |
| 修改 | `backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/vo/ChunkResponse.java` |
| 修改 | `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java` |
| 修改 | `backend/ai-server/src/main/resources/application.yaml` |

---

## Task 1: 扩展 ChunkResponse 字段

**Files:**
- Modify: `backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/vo/ChunkResponse.java`

- [ ] **Step 1: 添加 bm25Score 和 vectorScore 字段**

在 `ChunkResponse.java` 中添加两个新字段，用于存储分项分数：

```java
@Schema(description = "BM25分数")
private Float bm25Score;

@Schema(description = "向量相似度分数")
private Float vectorScore;
```

- [ ] **Step 2: 添加 getter/setter（Lombok @Data 会自动生成，但需要确保字段有默认值）**

使用 `@Builder.Default` 确保字段有默认值：

```java
@Builder.Default
private Float bm25Score = 0f;

@Builder.Default
private Float vectorScore = 0f;
```

- [ ] **Step 3: Commit**

```bash
git add backend/ai-pojo/src/main/java/top/hazenix/hazeaihub/vo/ChunkResponse.java
git commit -m "feat(astra): add bm25Score and vectorScore fields to ChunkResponse"
```

---

## Task 2: 创建 AstraProperties 配置类

**Files:**
- Create: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/AstraProperties.java`
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/ModelConfiguration.java` (注入 DashScopeApi Bean)
- Modify: `backend/ai-server/src/main/resources/application.yaml`

- [ ] **Step 1: 创建 AstraProperties 配置类**

```java
package top.hazenix.hazeaihub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "astra")
public class AstraProperties {

    private QueryRewrite queryRewrite = new QueryRewrite();
    private Search search = new Search();
    private Rerank rerank = new Rerank();

    @Data
    public static class QueryRewrite {
        private boolean enabled = true;
        private String model = "qwen-turbo";
        private int maxTokens = 128;
        private double temperature = 0.3;
    }

    @Data
    public static class Search {
        private Fusion fusion = new Fusion();
        private TopK topK = new TopK();

        @Data
        public static class Fusion {
            private double alpha = 0.5;
            private double threshold = 0.3;
        }

        @Data
        public static class TopK {
            private int bm25 = 50;
            private int vector = 50;
        }
    }

    @Data
    public static class Rerank {
        private boolean enabled = true;
        private String model = "bge-reranker-v2-m3";
        private int topK = 10;
    }
}
```

- [ ] **Step 2: 在 ModelConfiguration 中确保 DashScopeApi Bean 可用**

检查现有 `ModelConfiguration.java`，确认 `DashScopeApi` 已定义为 Bean（已存在）。如需复用，在 `QueryRewriteServiceImpl` 中直接注入即可。

- [ ] **Step 3: 更新 application.yaml 添加配置项**

在 `astra` 配置块下添加：

```yaml
astra:
  query-rewrite:
    enabled: true
    model: qwen-turbo
    max-tokens: 128
    temperature: 0.3
  search:
    fusion:
      alpha: 0.5
      threshold: 0.3
    top-k:
      bm25: 50
      vector: 50
  rerank:
    enabled: true
    model: bge-reranker-v2-m3
    top-k: 10
```

- [ ] **Step 4: Commit**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/AstraProperties.java
git add backend/ai-server/src/main/resources/application.yaml
git commit -m "feat(astra): add AstraProperties for query-rewrite, search fusion, and rerank config"
```

---

## Task 3: 创建 QueryRewriteService

**Files:**
- Create: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/IQueryRewriteService.java`
- Create: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/QueryRewriteServiceImpl.java`

- [ ] **Step 1: 创建 IQueryRewriteService 接口**

```java
package top.hazenix.hazeaihub.service;

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

- [ ] **Step 2: 创建 QueryRewriteServiceImpl 实现**

```java
package top.hazenix.hazeaihub.service.impl;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.common.DashScopeMessage;
import com.alibaba.cloud.ai.dashscope.common.DashScopeMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import top.hazenix.hazeaihub.config.AstraProperties;
import top.hazenix.hazeaihub.service.IQueryRewriteService;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryRewriteServiceImpl implements IQueryRewriteService {

    private final DashScopeApi dashScopeApi;
    private final AstraProperties astraProperties;

    private static final String REWRITE_PROMPT = """
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

            输入："%s"
            输出：
            """;

    @Override
    public String rewrite(String query) {
        if (!isEnabled()) {
            return query;
        }

        try {
            DashScopeChatModel chatModel = DashScopeChatModel.builder()
                    .dashScopeApi(dashScopeApi)
                    .defaultOptions(DashScopeChatOptions.builder()
                            .model(astraProperties.getQueryRewrite().getModel())
                            .maxTokens(astraProperties.getQueryRewrite().getMaxTokens())
                            .temperature(astraProperties.getQueryRewrite().getTemperature())
                            .build())
                    .build();

            String promptText = String.format(REWRITE_PROMPT, query);

            List<DashScopeMessage> messages = Arrays.asList(
                    DashScopeMessage.system().content("你是一个查询改写助手。").build(),
                    DashScopeMessage.user().content(promptText).build()
            );

            Prompt prompt = new Prompt(DashScopeMessages.toSpringAiMessages(messages));
            ChatResponse response = chatModel.call(prompt);

            if (response != null && response.getResult() != null
                    && response.getResult().getOutput() != null) {
                String rewritten = response.getResult().getOutput().getText().trim();
                log.debug("Query重写成功: {} -> {}", query, rewritten);
                return rewritten;
            }

            log.warn("Query重写返回空结果，使用原查询: {}", query);
            return query;

        } catch (Exception e) {
            log.error("Query重写异常，使用原查询: {}", query, e);
            return query;
        }
    }

    @Override
    public boolean isEnabled() {
        return astraProperties.getQueryRewrite().isEnabled();
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/IQueryRewriteService.java
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/QueryRewriteServiceImpl.java
git commit -m "feat(astra): add QueryRewriteService with qwen-turbo for query rewriting"
```

---

## Task 4: 重构 AstraSearchServiceImpl - 集成 QueryRewrite 和阈值过滤

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java`

- [ ] **Step 1: 添加新的依赖注入和字段**

在 `AstraSearchServiceImpl` 类中添加：

```java
private final IQueryRewriteService queryRewriteService;
private final AstraProperties astraProperties;

// 添加常量（从配置读取）
private static final double FUSION_ALPHA = 0.5;  // 将在方法中从配置读取
private static final double FUSION_THRESHOLD = 0.3;
```

- [ ] **Step 2: 修改 hybridSearch 方法签名和实现**

原方法：
```java
public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK)
```

修改后的实现流程：
```java
@Override
public List<ChunkResponse> hybridSearch(Long libraryId, String query, int topK) {
    log.debug("混合检索: libraryId={}, query={}", libraryId, query);

    // 1. 检查知识库是否有数据
    long chunkCount = chunkMapper.countByLibraryId(libraryId);
    if (chunkCount == 0) {
        throw new BusinessException(ErrorCode.ASTRA_LIBRARY_EMPTY);
    }

    // 2. Query 重写（可选）
    String rewrittenQuery = queryRewriteService.rewrite(query);

    // 3. BM25 + 向量双路召回（记录分项分数）
    double alpha = astraProperties.getSearch().getFusion().getAlpha();
    int bm25TopK = astraProperties.getSearch().getTopK().getBm25();
    int vectorTopK = astraProperties.getSearch().getTopK().getVector();

    List<ChunkResponse> bm25Results = bm25Search(libraryId, rewrittenQuery, bm25TopK);
    List<ChunkResponse> vectorResults = vectorSearch(libraryId, rewrittenQuery, vectorTopK);

    // 4. QA 向量检索
    List<ChunkResponse> qaResults = qaVectorSearch(libraryId, rewrittenQuery, QA_TOP_K);

    // 5. 归一化 + 加权融合 + 阈值过滤
    List<ChunkResponse> fusedResults = normalizeAndFuse(bm25Results, vectorResults, alpha);

    // 6. RRF 合并去重（包含 QA 结果）
    List<ChunkResponse> mergedResults = rrfMerge(bm25Results, vectorResults, qaResults, topK);

    // 7. 阈值过滤
    double threshold = astraProperties.getSearch().getFusion().getThreshold();
    List<ChunkResponse> filteredResults = thresholdFilter(mergedResults, threshold);

    return filteredResults;
}
```

- [ ] **Step 3: 添加 normalizeAndFuse 方法**

```java
/**
 * 归一化 + 加权融合 BM25 和向量分数
 */
private List<ChunkResponse> normalizeAndFuse(List<ChunkResponse> bm25Results,
                                               List<ChunkResponse> vectorResults,
                                               double alpha) {
    // 找最大分数用于归一化
    double maxBm25Score = bm25Results.stream()
            .mapToDouble(c -> c.getBm25Score() != null ? c.getBm25Score() : 0)
            .max().orElse(1.0);

    double maxVectorScore = vectorResults.stream()
            .mapToDouble(c -> c.getVectorScore() != null ? c.getVectorScore() : 0)
            .max().orElse(1.0);

    // 避免除以零
    if (maxBm25Score == 0) maxBm25Score = 1.0;
    if (maxVectorScore == 0) maxVectorScore = 1.0;

    // 归一化并融合
    Map<Long, ChunkResponse> chunkMap = new HashMap<>();
    Map<Long, Double> fusionScores = new HashMap<>();

    // 处理 BM25 结果
    for (ChunkResponse chunk : bm25Results) {
        chunkMap.put(chunk.getId(), chunk);
        double normalizedBm25 = (chunk.getBm25Score() != null ? chunk.getBm25Score() : 0) / maxBm25Score;
        double currentScore = fusionScores.getOrDefault(chunk.getId(), 0.0);
        fusionScores.put(chunk.getId(), currentScore + alpha * normalizedBm25);
    }

    // 处理向量结果
    for (ChunkResponse chunk : vectorResults) {
        chunkMap.put(chunk.getId(), chunk);
        double normalizedVector = (chunk.getVectorScore() != null ? chunk.getVectorScore() : 0) / maxVectorScore;
        double currentScore = fusionScores.getOrDefault(chunk.getId(), 0.0);
        fusionScores.put(chunk.getId(), currentScore + (1 - alpha) * normalizedVector);
    }

    // 返回融合后的结果
    return fusionScores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .map(entry -> {
                ChunkResponse chunk = chunkMap.get(entry.getKey());
                chunk.setScore((float) (entry.getValue() * 100)); // 缩放分数便于阅读
                return chunk;
            })
            .collect(Collectors.toList());
}
```

- [ ] **Step 4: 添加 thresholdFilter 方法**

```java
/**
 * 阈值过滤：保留融合分 >= threshold 的 chunks
 */
private List<ChunkResponse> thresholdFilter(List<ChunkResponse> chunks, double threshold) {
    return chunks.stream()
            .filter(c -> c.getScore() != null && c.getScore() >= threshold * 100) // 分数已缩放
            .collect(Collectors.toList());
}
```

- [ ] **Step 5: 修改 bm25Search 和 vectorSearch 以记录分项分数**

`bm25Search` 中，计算完分数后设置：
```java
response.setBm25Score(entry.getValue().floatValue());
```

`vectorSearch` 中，从 SQL 结果获取相似度后设置：
```java
ChunkResponse response = toChunkResponse(chunk, null);
response.setVectorScore((float) similarity);  // similarity 来自 SQL 的 1 - cosine_distance
```

- [ ] **Step 6: Commit**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java
git commit -m "feat(astra): integrate QueryRewrite and threshold filtering in hybridSearch"
```

---

## Task 5: 实现 ReRank 重排序

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java`
- Add: Rerank API 调用逻辑

- [ ] **Step 1: 调研 DashScope ReRank API 调用方式**

DashScope 的 ReRank API 通常通过 `DashScopeApi` 直接调用。需要检查 `com.alibaba.cloud.ai.dashscope` 包中是否有 `RerankRequest` 或类似类。

如果 Spring AI Alibaba 有 `DashScopeRerankModel`，使用方式类似 `DashScopeChatModel`。如果没有，则需要使用 `DashScopeApi` 的通用调用方式。

- [ ] **Step 2: 实现 rerank 方法**

假设使用 `DashScopeApi` 直接调用，示例实现：

```java
@Override
public List<ChunkResponse> rerank(Long libraryId, String query, List<ChunkResponse> chunks, int topK) {
    if (!astraProperties.getRerank().isEnabled()) {
        return chunks.subList(0, Math.min(chunks.size(), topK));
    }

    if (chunks == null || chunks.isEmpty()) {
        return chunks;
    }

    try {
        log.debug("ReRank重排序: libraryId={}, query={}, chunks={}", libraryId, query, chunks.size());

        // 1. 构造 API 请求
        String model = astraProperties.getRerank().getModel();
        List<String> documents = chunks.stream()
                .map(ChunkResponse::getContent)
                .collect(Collectors.toList());

        // 2. 调用 DashScope Rerank API
        // 注意：实际 API 调用方式取决于 DashScope SDK 的版本和可用类
        // 以下为伪代码示例：
        //
        // RerankRequest request = RerankRequest.builder()
        //         .model(model)
        //         .query(query)
        //         .documents(documents)
        //         .topK(topK)
        //         .build();
        //
        // RerankResponse response = dashScopeApi.rerank(request);
        //
        // 3. 更新 chunks 的分数
        // for (RerankResult result : response.getResults()) {
        //     chunks.get(result.getIndex()).setScore((float) result.getRelevanceScore());
        // }

        // 4. 按分数排序返回
        return chunks.stream()
                .sorted(Comparator.comparing(c -> c.getScore() != null ? c.getScore() : 0f, Comparator.reverseOrder()))
                .limit(topK)
                .collect(Collectors.toList());

    } catch (Exception e) {
        log.error("ReRank调用失败，退回原始顺序", e);
        return chunks.subList(0, Math.min(chunks.size(), topK));
    }
}
```

**注意：** 上述代码中 Rerank API 调用部分需要根据实际 DashScope SDK 版本调整。如果 SDK 不支持直接的 ReRank 模型调用，可能需要使用 HTTP 客户端（如 RestTemplate 或 WebClient）直接调用 DashScope API。

- [ ] **Step 3: 更新 chat 方法中的调用逻辑**

在 `chat()` 方法中，`hybridSearch` 返回的结果会直接传入 `rerank()`，无需修改调用链。

- [ ] **Step 4: Commit**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraSearchServiceImpl.java
git commit -m "feat(astra): add ReRank implementation with bge-reranker-v2-m3"
```

---

## Task 6: 集成测试与验证

**Files:**
- Test: `backend/ai-server/src/test/java/top/hazenix/hazeaihub/service/AstraSearchServiceTest.java` (如果测试目录存在)

- [ ] **Step 1: 本地启动验证**

```bash
cd backend && mvn spring-boot:run -pl ai-server
```

检查日志中是否有：
- QueryRewriteService 是否正常初始化
- hybridSearch 是否正确执行

- [ ] **Step 2: 功能测试**

使用 Astra 功能测试一个知识库的问答流程，验证：
1. Query 重写是否生效（可通过日志查看重写后的 query）
2. 阈值过滤是否生效（对比过滤前后的 chunk 数量）
3. ReRank 是否生效（返回结果的顺序是否有变化）

- [ ] **Step 3: Commit 最终版本**

```bash
git add -A
git commit -m "feat(astra): complete RAG enhancement - query rewrite, threshold filter, and rerank"
```

---

## 自检清单

### Spec 覆盖率检查

- [x] Phase 1 Query 重写 - Task 3
- [x] Phase 2 阈值过滤 - Task 4
- [x] Phase 3 ReRank - Task 5
- [x] ChunkResponse 字段扩展 - Task 1
- [x] 配置项 - Task 2

### 占位符扫描

- ReRank API 调用部分标注了"伪代码示例"，需要根据实际 SDK 调整。这是预期的，因为 DashScope SDK 版本需要现场确认。

### 类型一致性

- `ChunkResponse.bm25Score` 和 `ChunkResponse.vectorScore` 在 Task 1 中定义
- Task 4 的 `bm25Search` 和 `vectorSearch` 设置这两个字段
- Task 4 的 `normalizeAndFuse` 读取这两个字段
- Task 4 的 `thresholdFilter` 使用 `getScore()`（融合后的分数）

类型一致性已验证。

---

## 执行方式选择

**Plan complete and saved to `docs/superpowers/plans/2026-03-31-rag-enhancement-implementation-plan.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
