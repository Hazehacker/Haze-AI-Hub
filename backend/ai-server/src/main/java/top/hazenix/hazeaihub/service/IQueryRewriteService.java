package top.hazenix.hazeaihub.service;

public interface IQueryRewriteService {

    /**
     * 重写查询文本
     * @param query 原始查询
     * @return 重写后的查询
     */
    String rewrite(String query);

    /**
     * 是否启用 Query 重写
     */
    boolean isEnabled();
}