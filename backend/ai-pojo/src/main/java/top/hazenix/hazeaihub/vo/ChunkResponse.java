package top.hazenix.hazeaihub.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 知识库分片响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库分片响应")
public class ChunkResponse {

    @Schema(description = "分片ID")
    private Long id;

    @Schema(description = "所属知识库ID")
    private Long libraryId;

    @Schema(description = "关联媒体文件ID")
    private Long mediaId;

    @Schema(description = "原始文本内容")
    private String content;

    @Schema(description = "分片序号")
    private Integer chunkIndex;

    @Schema(description = "扩展元信息")
    private Map<String, Object> metadata;

    @Schema(description = "相关度分数(检索时填充)")
    private Float score;

    @Schema(description = "来源信息(检索时填充，格式: 文件名-页码)")
    private String source;

    @Builder.Default
    @Schema(description = "BM25分数")
    private Float bm25Score = 0f;

    @Builder.Default
    @Schema(description = "向量相似度分数")
    private Float vectorScore = 0f;
}
