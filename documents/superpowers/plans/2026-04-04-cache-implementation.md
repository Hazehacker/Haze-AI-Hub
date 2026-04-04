# 缓存实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为模型列表、分组列表、知识库列表添加两级缓存（Caffeine + Redis），提升查询性能

**Architecture:** 两级缓存架构：Caffeine 本地缓存 → Redis 分布式缓存 → 数据库。查询时先查本地，再查分布式，最后查数据库并回填两级缓存。写入时先写数据库，再写 Redis。

**Tech Stack:** Caffeine、Spring Data Redis (StringRedisTemplate)、Redisson (已有)、Hutool JSON

---

## 文件结构

```
ai-server/src/main/java/top/hazenix/hazeaihub/
├── constant/
│   └── CacheConstants.java          # 新增：缓存键常量
├── utils/
│   └── CacheUtil.java               # 新增：缓存工具类
├── config/
│   └── CacheConfiguration.java     # 新增：Caffeine 配置
└── service/impl/
    ├── ModelServiceImpl.java        # 修改：添加缓存逻辑
    ├── GroupServiceImpl.java        # 修改：添加缓存逻辑
    └── AstraLibraryServiceImpl.java # 修改：添加缓存逻辑
```

---

## Task 1: 添加 Caffeine 依赖

**Files:**
- Modify: `backend/ai-server/pom.xml`

- [ ] **Step 1: 添加 Caffeine 依赖**

在 `pom.xml` 的 `<dependencies>` 中添加：

```xml
<!-- Caffeine 本地缓存 -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

- [ ] **Step 2: 提交**

```bash
git add backend/ai-server/pom.xml
git commit -m "feat(cache): 添加 Caffeine 依赖"
```

---

## Task 2: 创建缓存常量类

**Files:**
- Create: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/constant/CacheConstants.java`

- [ ] **Step 1: 创建 CacheConstants.java**

```java
package top.hazenix.hazeaihub.constant;

/**
 * 缓存常量定义
 */
public final class CacheConstants {

    private CacheConstants() {
    }

    // ==================== 缓存键 ====================

    /** 模型列表缓存键 */
    public static final String MODEL_LIST_KEY = "cache:models";

    /** 分组列表缓存键前缀 */
    public static final String GROUP_LIST_KEY_PREFIX = "cache:groups:userId:";

    /** 知识库列表缓存键前缀 */
    public static final String LIBRARY_LIST_KEY_PREFIX = "cache:libraries:userId:";

    // ==================== Caffeine 本地缓存配置 ====================

    /** 模型列表本地缓存名 */
    public static final String CAFFEINE_MODEL_LIST = "models";

    /** 分组列表本地缓存名 */
    public static final String CAFFEINE_GROUP_LIST = "groups";

    /** 知识库列表本地缓存名 */
    public static final String CAFFEINE_LIBRARY_LIST = "libraries";

    // ==================== TTL 配置 ====================

    /** 缓存基础 TTL（小时） */
    public static final long BASE_TTL_HOURS = 1;

    /** 随机 TTL 最大偏移量（分钟） */
    public static final long RANDOM_TTL_MAX_MINUTES = 10;

    /** 获取分组列表缓存键 */
    public static String getGroupListKey(Long userId) {
        return GROUP_LIST_KEY_PREFIX + userId;
    }

    /** 获取知识库列表缓存键 */
    public static String getLibraryListKey(Long userId) {
        return LIBRARY_LIST_KEY_PREFIX + userId;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/constant/CacheConstants.java
git commit -m "feat(cache): 添加缓存常量定义"
```

---

## Task 3: 创建缓存工具类

**Files:**
- Create: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/utils/CacheUtil.java`

- [ ] **Step 1: 创建 CacheUtil.java**

```java
package top.hazenix.hazeaihub.utils;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 缓存工具类
 * <p>
 * 提供 Caffeine 本地缓存 + Redis 分布式缓存的两级缓存能力
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final CacheManager cacheManager;

    // ==================== 查询操作（两级缓存穿透） ====================

    /**
     * 两级缓存查询（穿透模式）
     * <p>
     * 流程：Caffeine → Redis → 数据库
     * </p>
     *
     * @param localCacheName  Caffeine 缓存名称
     * @param redisKey        Redis 缓存键
     * @param type            返回类型
     * @param dbFallback      数据库查询函数
     * @param time            TTL 时间
     * @param unit            TTL 时间单位
     * @return 查询结果
     */
    public <T> T queryWithPassThrough(String localCacheName, String redisKey, Class<T> type,
                                      Supplier<T> dbFallback, Long time, TimeUnit unit) {
        // 1. 查询 Caffeine 本地缓存
        Cache localCache = cacheManager.getCache(localCacheName);
        if (localCache != null) {
            T localValue = localCache.get(redisKey, type);
            if (localValue != null) {
                log.debug("Cache hit (Caffeine): key={}", redisKey);
                return localValue;
            }
        }

        // 2. 查询 Redis 分布式缓存
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (json != null) {
            // 命中空值
            if ("".equals(json)) {
                return null;
            }
            T value = JSONUtil.toBean(json, type);
            // 回填本地缓存
            if (localCache != null) {
                localCache.put(redisKey, value);
            }
            log.debug("Cache hit (Redis): key={}", redisKey);
            return value;
        }

        // 3. 查询数据库
        T value = dbFallback.get();

        // 4. 写入缓存
        if (value != null) {
            setWithRandomExpire(redisKey, value, time, unit);
            if (localCache != null) {
                localCache.put(redisKey, value);
            }
        } else {
            // 防止缓存穿透：写入空值
            stringRedisTemplate.opsForValue().set(redisKey, "", time, unit);
        }

        return value;
    }

    // ==================== 写入操作 ====================

    /**
     * 写入缓存（随机过期时间，防止缓存雪崩）
     *
     * @param key   缓存键
     * @param value 缓存值
     * @param time  基础过期时间
     * @param unit  时间单位
     */
    public void setWithRandomExpire(String key, Object value, Long time, TimeUnit unit) {
        long randomOffset = ThreadLocalRandom.current().nextLong(10);
        long finalTime = time + randomOffset;
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), finalTime, unit);
        log.debug("Cache write: key={}, ttl={}{}", key, finalTime, unit);
    }

    // ==================== 删除操作 ====================

    /**
     * 删除缓存（同时删除 Redis 和本地缓存）
     *
     * @param localCacheName  Caffeine 缓存名称
     * @param redisKey         Redis 缓存键
     */
    public void delete(String localCacheName, String redisKey) {
        // 删除 Redis
        stringRedisTemplate.delete(redisKey);
        // 删除本地缓存
        Cache localCache = cacheManager.getCache(localCacheName);
        if (localCache != null) {
            localCache.evict(redisKey);
        }
        log.debug("Cache delete: key={}", redisKey);
    }

    /**
     * 删除用户相关缓存（同时删除 Redis 和本地缓存）
     *
     * @param localCacheName  Caffeine 缓存名称
     * @param keyPrefix       Redis 缓存键前缀
     * @param userId          用户ID
     */
    public void deleteWithUserId(String localCacheName, String keyPrefix, Long userId) {
        String redisKey = keyPrefix + userId;
        delete(localCacheName, redisKey);
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/utils/CacheUtil.java
git commit -m "feat(cache): 添加缓存工具类"
```

---

## Task 4: 创建 Caffeine 配置类

**Files:**
- Create: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/CacheConfiguration.java`

- [ ] **Step 1: 创建 CacheConfiguration.java**

```java
package top.hazenix.hazeaihub.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.TimeUnit;

/**
 * 缓存配置类
 */
@Configuration
public class CacheConfiguration {

    /**
     * Caffeine 本地缓存管理器
     */
    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.registerCustomCache(
                "models",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );
        cacheManager.registerCustomCache(
                "groups",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );
        cacheManager.registerCustomCache(
                "libraries",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .recordStats()
                        .build()
        );
        return cacheManager;
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/config/CacheConfiguration.java
git commit -m "feat(cache): 添加 Caffeine 缓存配置"
```

---

## Task 5: 为 ModelServiceImpl 添加缓存

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/ModelServiceImpl.java`

- [ ] **Step 1: 修改 ModelServiceImpl.java**

在 `ModelServiceImpl.java` 中：

1. 添加依赖注入：
```java
private final CacheUtil cacheUtil;
```

2. 修改 `listModels()` 方法：
```java
@Override
public List<ModelDTO> listModels() {
    return cacheUtil.queryWithPassThrough(
            CacheConstants.CAFFEINE_MODEL_LIST,
            CacheConstants.MODEL_LIST_KEY,
            new com.fasterxml.jackson.core.type.TypeReference<List<ModelDTO>>() {},
            this::listModelsFromDB,
            CacheConstants.BASE_TTL_HOURS,
            TimeUnit.HOURS
    );
}

private List<ModelDTO> listModelsFromDB() {
    List<Model> models = modelMapper.selectList(new LambdaQueryWrapper<Model>()
            .eq(Model::getStatus, true)
            .orderByDesc(Model::getSort)
    );
    if (models == null) {
        throw new RuntimeException("模型列表为空");
    }
    return BeanUtil.copyToList(models, ModelDTO.class);
}
```

3. 修改 `addModel()` 方法，在 `modelMapper.insert(model);` 后添加：
```java
// 清除缓存
cacheUtil.delete(CacheConstants.CAFFEINE_MODEL_LIST, CacheConstants.MODEL_LIST_KEY);
```

4. 修改 `deleteModel()` 方法，在 `modelMapper.deleteById(id);` 后添加：
```java
// 清除缓存
cacheUtil.delete(CacheConstants.CAFFEINE_MODEL_LIST, CacheConstants.MODEL_LIST_KEY);
```

5. 添加必要的 import：
```java
import java.util.concurrent.TimeUnit;
import top.hazenix.hazeaihub.constant.CacheConstants;
import top.hazenix.hazeaihub.utils.CacheUtil;
```

- [ ] **Step 2: 验证编译**

```bash
cd backend && mvn compile -pl ai-server -am -q
```

- [ ] **Step 3: 提交**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/ModelServiceImpl.java
git commit -m "feat(cache): 为 ModelServiceImpl 添加缓存"
```

---

## Task 6: 为 GroupServiceImpl 添加缓存

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/GroupServiceImpl.java`

- [ ] **Step 1: 修改 GroupServiceImpl.java**

1. 添加依赖注入：
```java
private final CacheUtil cacheUtil;
```

2. 修改 `queryGroup()` 方法：
```java
@Override
public List<Group> queryGroup() {
    Long currentId = BaseContext.getCurrentId();
    String redisKey = CacheConstants.getGroupListKey(currentId);

    return cacheUtil.queryWithPassThrough(
            CacheConstants.CAFFEINE_GROUP_LIST,
            redisKey,
            new com.fasterxml.jackson.core.type.TypeReference<List<Group>>() {},
            () -> queryGroupFromDB(currentId),
            CacheConstants.BASE_TTL_HOURS,
            TimeUnit.HOURS
    );
}

private List<Group> queryGroupFromDB(Long userId) {
    return groupMapper.selectList(
            new LambdaQueryWrapper<Group>()
                    .eq(Group::getUserId, userId)
                    .orderByDesc(Group::getSort)
    );
}
```

3. 修改 `addGroup()` 方法，在 `groupMapper.insert(group);` 后添加：
```java
// 清除缓存
cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_GROUP_LIST,
        CacheConstants.GROUP_LIST_KEY_PREFIX, group.getUserId());
```

4. 修改 `deleteGroup()` 方法，在 `groupMapper.deleteById(id);` 后添加：
```java
// 清除缓存
if (group != null) {
    cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_GROUP_LIST,
            CacheConstants.GROUP_LIST_KEY_PREFIX, group.getUserId());
}
```

5. 修改 `updateGroup()` 方法，在 `groupMapper.update(updateWrapper);` 后添加：
```java
// 清除缓存
cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_GROUP_LIST,
        CacheConstants.GROUP_LIST_KEY_PREFIX, group.getUserId());
```

6. 添加 import：
```java
import java.util.concurrent.TimeUnit;
import top.hazenix.hazeaihub.constant.CacheConstants;
import top.hazenix.hazeaihub.utils.CacheUtil;
```

- [ ] **Step 2: 验证编译**

```bash
cd backend && mvn compile -pl ai-server -am -q
```

- [ ] **Step 3: 提交**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/GroupServiceImpl.java
git commit -m "feat(cache): 为 GroupServiceImpl 添加缓存"
```

---

## Task 7: 为 AstraLibraryServiceImpl 添加缓存

**Files:**
- Modify: `backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraLibraryServiceImpl.java`

- [ ] **Step 1: 修改 AstraLibraryServiceImpl.java**

1. 添加依赖注入：
```java
private final CacheUtil cacheUtil;
```

2. 修改 `listLibraries()` 方法：
```java
@Override
public List<LibraryResponse> listLibraries(Long userId, String keyword, Integer page, Integer size) {
    String redisKey = CacheConstants.getLibraryListKey(userId);

    return cacheUtil.queryWithPassThrough(
            CacheConstants.CAFFEINE_LIBRARY_LIST,
            redisKey,
            new com.fasterxml.jackson.core.type.TypeReference<List<LibraryResponse>>() {},
            () -> listLibrariesFromDB(userId, keyword),
            CacheConstants.BASE_TTL_HOURS,
            TimeUnit.HOURS
    );
}

private List<LibraryResponse> listLibrariesFromDB(Long userId, String keyword) {
    log.debug("从数据库获取知识库列表: userId={}, keyword={}", userId, keyword);
    List<KbLibrary> libraries = libraryMapper.listByOwnerWithStats(userId, keyword);
    return libraries.stream()
            .map(lib -> toResponse(lib, getMediaCount(lib.getId()), getChunkCount(lib.getId())))
            .collect(Collectors.toList());
}
```

3. 修改 `createLibrary()` 方法，在 `libraryMapper.insert(library);` 后添加：
```java
// 清除缓存
cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_LIBRARY_LIST,
        CacheConstants.LIBRARY_LIST_KEY_PREFIX, userId);
```

4. 修改 `updateLibrary()` 方法，在 `libraryMapper.updateById(library);` 后添加：
```java
// 清除缓存
cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_LIBRARY_LIST,
        CacheConstants.LIBRARY_LIST_KEY_PREFIX, userId);
```

5. 修改 `deleteLibrary()` 方法，在 `libraryMapper.deleteById(libraryId);` 后添加：
```java
// 清除缓存
cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_LIBRARY_LIST,
        CacheConstants.LIBRARY_LIST_KEY_PREFIX, userId);
```

6. 修改 `toggleTop()` 方法，在 `libraryMapper.updateById(library);` 后添加：
```java
// 清除缓存
cacheUtil.deleteWithUserId(CacheConstants.CAFFEINE_LIBRARY_LIST,
        CacheConstants.LIBRARY_LIST_KEY_PREFIX, userId);
```

7. 添加 import：
```java
import java.util.concurrent.TimeUnit;
import top.hazenix.hazeaihub.constant.CacheConstants;
import top.hazenix.hazeaihub.utils.CacheUtil;
```

- [ ] **Step 2: 验证编译**

```bash
cd backend && mvn compile -pl ai-server -am -q
```

- [ ] **Step 3: 提交**

```bash
git add backend/ai-server/src/main/java/top/hazenix/hazeaihub/service/impl/AstraLibraryServiceImpl.java
git commit -m "feat(cache): 为 AstraLibraryServiceImpl 添加缓存"
```

---

## Task 8: 验证与测试

- [ ] **Step 1: 启动应用验证**

```bash
cd backend && mvn spring-boot:run -pl ai-server
```

观察日志中是否有缓存相关的输出（`Cache write`、`Cache hit` 等）

- [ ] **Step 2: 测试 API**

1. 调用模型列表 API，多次请求验证缓存命中
2. 调用分组列表 API，验证用户隔离
3. 调用知识库列表 API，验证用户隔离
4. 执行增删改操作后，验证缓存正确失效

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat(cache): 完成缓存功能实施"
```

---

## 自检清单

- [ ] 模型列表缓存：查询/新增/删除后缓存正确工作
- [ ] 分组列表缓存：不同用户缓存隔离，新增/删除/更新后缓存失效
- [ ] 知识库列表缓存：同分组列表
- [ ] 缓存穿透防御：空值查询不会打爆数据库
- [ ] 缓存雪崩防御：TTL 随机偏移生效
- [ ] 应用启动正常，无报错
