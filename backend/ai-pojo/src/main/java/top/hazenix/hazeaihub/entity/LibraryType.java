package top.hazenix.hazeaihub.entity;

/**
 * 知识库类型枚举
 */
public enum LibraryType {
    /**
     * 个人知识库
     */
    PERSONAL("personal", "个人"),

    /**
     * 团队知识库
     */
    TEAM("team", "团队");

    private final String code;
    private final String description;

    LibraryType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static LibraryType fromCode(String code) {
        for (LibraryType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown library type: " + code);
    }
}
