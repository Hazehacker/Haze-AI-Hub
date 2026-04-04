# qwen3-rerank 接入实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ReRank 调用从 HTTP 手动实现替换为 DashScope SDK 调用，使用 `qwen3-rerank` 模型

**Architecture:** 使用 `dashscope-sdk-java` 2.9.2 的 `TextReRank` 类替换现有的 HTTP 手动调用，配置 model 为 `qwen3-rerank`

**Tech Stack:** Java 17, dashscope-sdk-java 2.9.2, Spring Boot

---

## 文件变更

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java` | 修改 | 替换 `callDashscopeRerank()` 和 `parseRerankResponse()` 方法 |
| `backend/ai-server/src/main/resources/application.yaml` | 修改 | `rerank.model` 从 `bge-reranker-v2-m3` 改为 `qwen3-rerank` |

---

## Task 1: 更新配置 model 为 qwen3-rerank

**Files:**
- Modify: `backend/ai-server/src/main/resources/application.yaml:140-142`

- [ ] **Step 1: 修改 rerank.model 配置**

```yaml
rerank:
  enabled: true
  model: qwen3-rerank  # 从 bge-reranker-v2-m3 改为 qwen3-rerank
  top-k: 10
```

- [ ] **Step 2: 提交配置变更**

```bash
git add backend/ai-server/src/main/resources/application.yaml
git commit -m "chore(astra): change rerank model to qwen3-rerank"
```

---

## Task 2: 替换 callDashscopeRerank() 为 SDK 调用

**Files:**
- Modify: `backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java:153-184`

- [ ] **Step 1: 添加 SDK import**

在文件头部添加：
```java
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankResult;
```

- [ ] **Step 2: 替换 callDashscopeRerank() 方法实现**

替换原有方法：

```java
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
    TextReRankResult result = rerank.call(TextReRankParam.builder()
        .model(astraProperties.getRerank().getModel())
        .query(query)
        .documents(documents)
        .topN(astraProperties.getRerank().getTopK())
        .returnDocuments(true)
        .build());

    // 解析结果
    return parseRerankResult(result, chunks);
}
```

- [ ] **Step 3: 提交**

```bash
git add backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java
git commit -m "refactor(astra): use DashScope SDK for qwen3-rerank"
```

---

## Task 3: 替换 parseRerankResponse() 为 parseRerankResult()

**Files:**
- Modify: `backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java:219-262`

- [ ] **Step 1: 替换 parseRerankResponse() 方法实现**

```java
/**
 * 解析 DashScope ReRank SDK 返回结果
 */
private List<ChunkResponse> parseRerankResult(TextReRankResult result, List<ChunkResponse> chunks) {
    try {
        if (result == null || result.getOutput() == null) {
            log.warn("ReRank 结果为空");
            return chunks;
        }

        // 提取 results 列表
        List<?> results = result.getOutput().getResults();
        if (results == null || results.isEmpty()) {
            log.warn("ReRank 响应中无 results");
            return chunks;
        }

        // 构建 index -> score 映射
        Map<Integer, Float> scoreMap = new HashMap<>();
        for (Object r : results) {
            // TextReRankResult.Item 包含 index 和 relevanceScore 字段
            // 使用反射或类型转换提取
            if (r instanceof TextReRankResult.Item) {
                TextReRankResult.Item item = (TextReRankResult.Item) r;
                scoreMap.put(item.getIndex(), (float) item.getRelevanceScore());
            }
        }

        // 更新 chunks 分数
        for (int i = 0; i < chunks.size(); i++) {
            ChunkResponse chunk = chunks.get(i);
            if (scoreMap.containsKey(i)) {
                chunk.setScore(scoreMap.get(i));
            }
        }

        // 按分数降序排序
        return chunks.stream()
            .sorted(Comparator.comparing(c -> c.getScore() != null ? c.getScore() : 0f, Comparator.reverseOrder()))
            .collect(Collectors.toList());

    } catch (Exception e) {
        log.error("解析 ReRank 结果失败: {}", e.getMessage());
        return chunks;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java
git commit -m "refactor(astra): parse SDK result for qwen3-rerank"
```

---

## Task 4: 删除不再使用的 HTTP 调用方法

**Files:**
- Modify: `backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java`

- [ ] **Step 1: 删除 callDashscopeApi() 方法**

删除整个方法（大约 189-214 行）：
```java
/**
 * 调用 DashScope API (HTTP)
 */
private String callDashscopeApi(String endpoint, String apiKey, Map<String, Object> requestBody) {
    // ... 方法体
}
```

- [ ] **Step 2: 删除不再需要的 import**

检查并删除：
```java
import com.fasterxml.jackson.databind.ObjectMapper;  // 如果只用于 HTTP 调用则删除
```

注意：如果 `ObjectMapper` 还用于其他地方（如 JSON 处理），则保留。

- [ ] **Step 3: 提交**

```bash
git add backend/ai-server/src/main/java/.../AstraSearchServiceImpl.java
git commit -m "refactor(astra): remove obsolete HTTP call method"
```

---

## Task 5: 验证构建

- [ ] **Step 1: 运行 Maven 构建**

```bash
cd backend && mvn clean compile -pl ai-server -am
```

预期：构建成功，无编译错误

- [ ] **Step 2: 如遇 SDK API 编译错误，根据实际错误调整类名**

**可能的类名差异：**

| 预期的类/方法 | 可能的备选 |
|--------------|-----------|
| `TextReRank` | `Rerank` |
| `TextReRankParam` | `TextReRankTaskParameters` |
| `TextReRankResult.Item` | 直接从 `getResults()` 取 Map，需提取 `index` 和 `relevanceScore` 字段 |
| `result.getOutput().getResults()` | `result.getOutput()` 直接是 List |

**调试方法：** 查看编译错误中的类名提示，或查阅 [DashScope Java SDK 文档](https://help.aliyun.com/zh/model-studio/install-sdk) 中的导入语句

- [ ] **Step 3: 如需调整解析逻辑，参考以下备选方案**

如果 `TextReRankResult.Item` 类型不存在，使用 Map 解析：

```java
private List<ChunkResponse> parseRerankResult(Object result, List<ChunkResponse> chunks) {
    // 假设 result.getOutput().getResults() 返回 List<Map<String, Object>>
    // 其中每个 Map 包含 index (Integer) 和 relevance_score (Double)
}
```

---

## 验证方式

1. 启动应用：`mvn spring-boot:run -pl ai-server`
2. 向已有文档的知识库提问
3. 观察日志：
   - `ReRank重排序: libraryId=..., query=..., chunks=..., topK=...`
   - `ReRank完成，返回{}个结果`
4. 确认返回结果的相关性

---

## 验证方式

1. 启动应用：`mvn spring-boot:run -pl ai-server`
2. 向已有文档的知识库提问
3. 观察日志：
   - `ReRank重排序: libraryId=..., query=..., chunks=..., topK=...`
   - `ReRank完成，返回{}个结果`
4. 确认返回结果的相关性
