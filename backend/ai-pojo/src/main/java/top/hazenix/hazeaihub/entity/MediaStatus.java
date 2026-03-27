package top.hazenix.hazeaihub.entity;

/**
 * 媒体文件解析状态枚举
 */
public enum MediaStatus {
    /**
     * 等待解析
     */
    PENDING("PENDING", "等待中"),

    /**
     * 解析中
     */
    PARSING("PARSING", "解析中"),

    /**
     * 已解析完成
     */
    PARSED("PARSED", "已解析"),

    /**
     * 解析失败
     */
    FAILED("FAILED", "解析失败");

    private final String code;
    private final String description;

    MediaStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static MediaStatus fromCode(String code) {
        for (MediaStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown media status: " + code);
    }
}
