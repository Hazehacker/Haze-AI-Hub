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