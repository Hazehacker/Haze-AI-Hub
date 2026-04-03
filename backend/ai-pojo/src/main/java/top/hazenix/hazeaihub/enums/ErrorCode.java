package top.hazenix.hazeaihub.enums;

/**
 * 错误码枚举
 */
public enum ErrorCode {

    // 通用错误码
    SUCCESS(200, "操作成功"),
    PARAM_INVALID(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // Astra 错误码 (ASTRA_xxx)
    ASTRA_LIBRARY_NOT_FOUND("ASTRA_001", "知识库不存在"),
    ASTRA_MEDIA_NOT_FOUND("ASTRA_002", "媒体文件不存在"),
    ASTRA_UNSUPPORTED_FILE_TYPE("ASTRA_003", "不支持的文件格式"),
    ASTRA_FILE_SIZE_EXCEEDED("ASTRA_004", "文件大小超出限制"),
    ASTRA_SHA256_MISMATCH("ASTRA_005", "SHA256校验失败"),
    ASTRA_PARSE_FAILED("ASTRA_006", "解析失败"),
    ASTRA_SESSION_NOT_FOUND("ASTRA_007", "会话不存在"),
    ASTRA_LIBRARY_EMPTY("ASTRA_008", "知识库为空，无法问答"),
    ASTRA_DUPLICATE_FILE("ASTRA_009", "文件已存在"),
    ASTRA_UPLOAD_FAILED("ASTRA_010", "文件上传失败");

    private final String code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = String.valueOf(code);
        this.message = message;
    }

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
