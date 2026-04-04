# 缓存设计方案

## 1. 背景与目标

当前项目存在高频查询场景（模型列表、分组列表、知识库列表），每次请求都直接查询数据库，造成不必要的数据库压力。本方案通过引入缓存层，提升系统响应速度，降低数据库负载。

## 2. 架构设计

### 2.1 两级缓存架构

```
请求 → Caffeine 本地缓存 → Redis 分布式缓存 → 数据库
         (热点数据)          (共享缓存)
```

- **Caffeine 本地缓存**：进程内缓存，访问速度极快，适合单实例部署
- **Redis 分布式缓存**：多实例共享，确保数据一致性

### 2.2 缓存流程

**查询流程：**
1. 先查询 Caffeine 本地缓存
2. 未命中则查询 Redis 分布式缓存
3. 未命中则查询数据库，并将结果写入两级缓存

**写入流程：**
1. 写入数据库
2. 写入 Redis 分布式缓存
3. 更新 Caffeine 本地缓存（或让其自然过期）

**失效流程：**
1. 删除数据库记录
2. 删除 Redis 缓存（Caffeine 自然过期）

## 3. 缓存内容

| 缓存项 | Caffeine 键 | Redis 键 | TTL | 说明 |
|--------|-------------|----------|-----|------|
| 模型列表 | `models` | `cache:models` | 1小时 + 随机偏移(0-10分钟) | 统一键，无用户隔离 |
| 分组列表 | `groups:{userId}` | `cache:groups:userId:{userId}` | 1小时 + 随机偏移 | 用户隔离 |
| 知识库列表 | `libraries:{userId}` | `cache:libraries:userId:{userId}` | 1小时 + 随机偏移 | 用户隔离 |

## 4. 防御措施

### 4.1 缓存穿透
- 将空值（null 或空列表）也写入缓存
- 缓存键：`""` 表示空值

### 4.2 缓存雪崩
- 基础 TTL 1小时 + 随机偏移量（0-10分钟）
- 防止大量缓存同时过期

### 4.3 缓存击穿
- 使用 Redisson 分布式锁
- 保证只有一个线程重建缓存

## 5. 文件变更

### 5.1 新增文件

| 文件 | 路径 | 说明 |
|------|------|------|
| CacheConstants | `ai-server/.../constant/CacheConstants.java` | 缓存键常量定义 |
| CacheUtil | `ai-server/.../utils/CacheUtil.java` | 缓存工具类（Caffeine + Redis） |

### 5.2 修改文件

| 文件 | 修改内容 |
|------|---------|
| `ModelServiceImpl.java` | 添加缓存查询/写入/失效逻辑 |
| `GroupServiceImpl.java` | 添加缓存查询/写入/失效逻辑 |
| `AstraLibraryServiceImpl.java` | 添加缓存查询/写入/失效逻辑 |
| `pom.xml` | 添加 Caffeine 依赖 |

## 6. 实现细节

### 6.1 CacheUtil 核心方法

```java
// 查询（穿透模式）
<T> T queryWithPassThrough(String key, Class<T> type, Supplier<T> dbFallback, Long time, TimeUnit unit)

// 写入
void setWithRandomExpire(String key, Object value, Long time, TimeUnit unit)

// 删除
void delete(String key)
void deleteWithPrefix(String keyPrefix)
```

### 6.2 服务层改造模式

```java
// 查询
public List<Model> listModels() {
    return cacheUtil.queryWithPassThrough(
        CacheConstants.MODEL_LIST_KEY,
        new TypeReference<List<Model>>() {},
        this::listModelsFromDB,
        1L, TimeUnit.HOURS
    );
}

// 新增/更新后
public void saveModel(Model model) {
    modelMapper.insert(model);
    cacheUtil.delete(CacheConstants.MODEL_LIST_KEY);
}

// 删除后
public void deleteModel(Long id) {
    modelMapper.deleteById(id);
    cacheUtil.delete(CacheConstants.MODEL_LIST_KEY);
}
```

## 7. 配置项

```yaml
haze:
  cache:
    # Caffeine 本地缓存配置
    caffeine:
      models:
        maximum-size: 100
        expire-after-write: 30m
      groups:
        maximum-size: 500
        expire-after-write: 30m
      libraries:
        maximum-size: 500
        expire-after-write: 30m
```

## 8. 测试要点

- [ ] 模型列表缓存：新增/删除模型后缓存正确失效
- [ ] 分组列表缓存：按用户隔离，不同用户缓存独立
- [ ] 知识库列表缓存：同分组列表
- [ ] 缓存穿透防御：查询不存在的数据不会打爆数据库
- [ ] 缓存雪崩防御：TTL 随机偏移生效
- [ ] 多实例模拟：重启服务后缓存行为正确
