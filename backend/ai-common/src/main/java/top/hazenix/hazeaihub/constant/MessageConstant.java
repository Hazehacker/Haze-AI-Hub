package top.hazenix.hazeaihub.constant;

/**
 * 信息提示常量类
 */
public class MessageConstant {

    public static final String PASSWORD_ERROR = "密码错误";
    public static final String ACCOUNT_NOT_FOUND = "账号不存在";
    public static final String ACCOUNT_LOCKED = "账号被锁定";
    public static final String UNKNOWN_ERROR = "未知错误";
    public static final String USER_NOT_LOGIN = "用户未登录";
    public static final String CATEGORY_BE_RELATED_BY_SETMEAL = "当前分类关联了套餐,不能删除";
    public static final String CATEGORY_BE_RELATED_BY_DISH = "当前分类关联了菜品,不能删除";
    public static final String SHOPPING_CART_IS_NULL = "购物车数据为空，不能下单";
    public static final String ADDRESS_BOOK_IS_NULL = "用户地址为空，不能下单";
    public static final String LOGIN_FAILED = "登录失败";
    public static final String UPLOAD_FAILED = "文件上传失败";
    public static final String SETMEAL_ENABLE_FAILED = "套餐内包含未启售菜品，无法启售";
    public static final String PASSWORD_EDIT_FAILED = "密码修改失败";
    public static final String DISH_ON_SALE = "起售中的菜品不能删除";
    public static final String SETMEAL_ON_SALE = "起售中的套餐不能删除";
    public static final String DISH_BE_RELATED_BY_SETMEAL = "当前菜品关联了套餐,不能删除";
    public static final String ORDER_STATUS_ERROR = "订单状态错误";
    public static final String ARTICLE_NOT_FOUND = "不存在该文章";
    public static final String ALREADY_EXISTS = "已存在";
    public static final String REPLYER_NOT_FOUND = "不存在该被回复者";
    public static final String ARTICLLE_CONTENT_IS_NULL = "文章内容不能为空";
    public static final String ARTICLE_SIZE_EXCEED_LIMIT = "文章字数超出限制";
    public static final String ARTICLE_FAVORITE_NEED_LOGIN = "登录之后才能收藏文章";
    public static final String DELETE_NOT_ALLOWED_THIS_CATEGORY_HAS_ARTICLES = "当前分类关联了文章，不能删除";

    public static final String DELETE_NOT_ALLOWED_THIS_TAG_HAS_RELATED_ARTICLE = "当前标签关联了文章，不能删除";
    public static final String CURRENT_EMAIL_NOT_REGISTERD = "当前邮箱还未注册";
    public static final String EMAIL_OR_PASSWORD_ERROR = "邮箱或密码错误";
    public static final String CURRENT_USER_IS_ILLEGAL = "当前用户已被拉入黑名单";
    public static final String CURRENT_EMAIL_HAS_REGISTERED = "当前邮箱已经注册过账号";
    public static final String NOT_AUTHED = "未授权";
    public static final String CODE_TOO_LONG = "授权码过长，怀疑恶意输入";
    public static final String GITHUB_AUTH_FAILED = "从github获取access token失败";
    public static final String EMAIL_FOEMAT_ERROR = "邮箱格式不正确";
    public static final String USERNAME_TOO_LONG = "用户昵称不超过30个字符";
    public static final String CURRENT_PASSWORD_ERROR = "当前密码填写错误";
    public static final String PASSWORD_NOT_CHANGE = "新密码不能和当前密码相同";
    public static final String ID_TOKEN_IS_EMPTY = "id_token不能为空";
    public static final String ID_TOKEN_IS_EXPIRED = "id_token已过期";

    public static final String ID_TOKEN_INVALID = "id_token无效";
    public static final String AUDIENCE_INVALID = "无效的audience";
    public static final String GOOGLE_LOGIN_ERROR = "google登录失败";
    public static final String PLAYLIST_BE_RELATED_BY_TRACK = "当前歌单关联了歌曲，不能删除";
    public static final String FILE_NOT_EXIST = "该歌曲的音频未上传";
    public static final String TRACK_NOT_EXIST = "不存在该歌曲";
    public static final String FILE_NOT_AUDIO = "该文件不是音频";
    public static final String FILE_NOT_IMAGE = "不支持的图片格式";
    public static final String PLAYLIST_TRACK_NOT_MATCH = "ids集合数量不匹配";
    public static final String LRC_ONLY = "仅支持.lrc文件";
    public static final String NO_AUTH_EDIT = "没有权限编辑这首歌的歌词";
    public static final String FILE_READ_ERROR = "读取歌词文件失败";
}
