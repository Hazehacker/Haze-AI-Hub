# qwen3-rerank 接入设计

## 背景

当前 RAG 流程中 ReRank 使用 HTTP 手动调用 `bge-reranker-v2-m3`，代码不够简洁。根据阿里云百炼文档，应使用 `dashscope-sdk-java` SDK 接入 `qwen3-rerank` 模型，获得更好的维护性和扩展性。

## 目标

将 ReRank 调用从 HTTP 手动实现替换为 DashScope SDK 调用，使用 `qwen3-rerank` 模型。

## 改动范围

仅修改 `AstraSearchServiceImpl.java` 中的 ReRank 相关代码。

## 实现方案

### 1. 依赖

`dashscope-sdk-java` 2.9.2 已存在于 `pom.xml` 中，无需新增依赖。

### 2. 代码改动

**替换 `callDashscopeRerank()` 方法：**

```java
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankTaskParameters;
import com.alibaba.dashscope.common.DashScopeResult;

/**
 * 调用 qwen3-rerank API 进行文档重排序
 */
private List<ChunkResponse> callDashscopeRerank(String query, List<ChunkResponse> chunks) {
    TextReRank rerank = new TextReRank();

    // 构建文档列表
    List<String> documents = chunks.stream()
        .map(chunk -> chunk.getContent() != null ? chunk.getContent() : "")
        .collect(Collectors.toList());

    // 构建参数
    TextReRankTaskParameters params = TextReRankTaskParameters.builder()
        .model(astraProperties.getRerank().getModel())  // qwen3-rerank
        .query(query)
        .documents(documents)
        .topN(astraProperties.getRerank().getTopK())
        .returnDocuments(true)
        .build();

    // 调用 SDK
    DashScopeResult result = rerank.call(params);

    // 解析结果
    return parseRerankResult(result, chunks);
}
```

**替换 `parseRerankResponse()` 为 `parseRerankResult()`：**

SDK 返回的 `DashScopeResult` 结构与 HTTP 响应不同，需要调整解析逻辑：

```java
private List<ChunkResponse> parseRerankResult(DashScopeResult result, List<ChunkResponse> chunks) {
    if (result == null || result.getOutput() == null) {
        log.warn("ReRank 结果为空");
        return chunks;
    }

    Object output = result.getOutput();
    // SDK output 是 DashScopeResult.Output 或类似结构
    // 提取 results 并按 index 排序

    // 构建 index -> score 映射
    Map<Integer, Float> scoreMap = new HashMap<>();
    List<?> results = result.getOutput().getResults();
    if (results == null) {
        return chunks;
    }

    for (Object r : results) {
        // 反射或类型转换提取 index 和 relevanceScore
        // 根据 SDK 实际返回结构实现
    }

    // 更新 chunks 分数并按分数降序排序
    for (int i = 0; i < chunks.size(); i++) {
        ChunkResponse chunk = chunks.get(i);
        if (scoreMap.containsKey(i)) {
            chunk.setScore(scoreMap.get(i));
        }
    }

    return chunks.stream()
        .sorted(Comparator.comparing(c -> c.getScore() != null ? c.getScore() : 0f, Comparator.reverseOrder()))
        .collect(Collectors.toList());
}
```

**删除不再使用的 `callDashscopeApi()` HTTP 调用方法。**

### 3. 配置更新

**`application.yaml` 更新 rerank.model 默认值：**

```yaml
rerank:
  enabled: true
  model: qwen3-rerank  # 从 bge-reranker-v2-m3 改为 qwen3-rerank
  top-k: 10
```

### 4. 错误处理

保持现有降级逻辑：
- SDK 调用失败时，打印日志并退回原始 chunks 顺序
- `rerank()` 方法已有 try-catch 包装，无需额外处理

## 不在本次范围内

- `AstraProperties` 结构变更
- QA 对检索（`qaVectorSearch`）
- 其他检索阶段改动

## 验证方式

1. 启动应用，向已有文档的知识库提问
2. 观察日志中 ReRank 是否正常调用
3. 确认返回结果的相关性是否提升
