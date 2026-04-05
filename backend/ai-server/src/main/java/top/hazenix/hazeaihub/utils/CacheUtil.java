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

import com.fasterxml.jackson.core.type.TypeReference;

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

    /**
     * 两级缓存查询（穿透模式，支持泛型类型）
     * <p>
     * 流程：Caffeine → Redis → 数据库
     * </p>
     *
     * @param localCacheName  Caffeine 缓存名称
     * @param redisKey        Redis 缓存键
     * @param typeRef         返回类型引用（支持泛型）
     * @param dbFallback      数据库查询函数
     * @param time            TTL 时间
     * @param unit            TTL 时间单位
     * @return 查询结果
     */
    public <T> T queryWithPassThrough(String localCacheName, String redisKey, TypeReference<T> typeRef,
                                      Supplier<T> dbFallback, Long time, TimeUnit unit) {
        // 1. 查询 Caffeine 本地缓存（存储为 JSON 字符串）
        Cache localCache = cacheManager.getCache(localCacheName);
        if (localCache != null) {
            String localJson = localCache.get(redisKey, String.class);
            if (localJson != null) {
                // 命中空值
                if ("".equals(localJson)) {
                    return null;
                }
                T value = JSONUtil.toBean(localJson, typeRef.getType(), false);
                log.debug("Cache hit (Caffeine): key={}", redisKey);
                return value;
            }
        }

        // 2. 查询 Redis 分布式缓存
        String json = stringRedisTemplate.opsForValue().get(redisKey);
        if (json != null) {
            // 命中空值
            if ("".equals(json)) {
                return null;
            }
            T value = JSONUtil.toBean(json, typeRef.getType(), false);
            // 回填本地缓存
            if (localCache != null) {
                localCache.put(redisKey, json);
            }
            log.debug("Cache hit (Redis): key={}", redisKey);
            return value;
        }

        // 3. 查询数据库
        T value = dbFallback.get();

        // 4. 写入缓存
        if (value != null) {
            String valueJson = JSONUtil.toJsonStr(value);
            setWithRandomExpire(redisKey, value, time, unit);
            if (localCache != null) {
                localCache.put(redisKey, valueJson);
            }
        } else {
            // 防止缓存穿透：写入空值
            stringRedisTemplate.opsForValue().set(redisKey, "", time, unit);
            if (localCache != null) {
                localCache.put(redisKey, "");
            }
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