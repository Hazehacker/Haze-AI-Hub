package top.hazenix.hazeaihub.entity;

/**
 * QA生成状态枚举
 */
public enum QaStatus {
    /**
     * 等待生成
     */
    PENDING("PENDING", "等待中"),

    /**
     * 生成中
     */
    GENERATING("GENERATING", "生成中"),

    /**
     * 已生成完成
     */
    GENERATED("GENERATED", "已生成"),

    /**
     * 生成失败
     */
    FAILED("FAILED", "生成失败");

    private final String code;
    private final String description;

    QaStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static QaStatus fromCode(String code) {
        for (QaStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown QA status: " + code);
    }
}
