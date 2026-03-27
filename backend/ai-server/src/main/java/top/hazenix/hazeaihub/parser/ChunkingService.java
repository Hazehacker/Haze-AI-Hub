package top.hazenix.hazeaihub.parser;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能分块服务
 */
@Slf4j
@Service
public class ChunkingService {

    // 分块目标大小 (按 token，约 1000-1200 tokens)
    private static final int TARGET_CHUNK_SIZE = 1000;

    // 重叠大小 (token 的 20%，最大 100 tokens)
    private static final int OVERLAP_SIZE = 100;

    // 最小分块大小
    private static final int MIN_CHUNK_SIZE = 100;

    /**
     * 对文本进行智能分块
     * @param text 原始文本
     * @return 分块列表
     */
    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return new ArrayList<>();
        }

        List<String> chunks = new ArrayList<>();

        // 1. 按段落分割
        String[] paragraphs = text.split("\n\n");

        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            // 跳过空段落
            if (paragraph.isBlank()) {
                continue;
            }

            int currentSize = estimateTokenSize(currentChunk.toString());
            int paragraphSize = estimateTokenSize(paragraph);

            // 如果当前块加上这个段落超过目标大小
            if (currentSize + paragraphSize > TARGET_CHUNK_SIZE) {
                // 保存当前块
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                }

                // 开始新块，添加重叠内容
                if (OVERLAP_SIZE > 0 && currentChunk.length() > OVERLAP_SIZE) {
                    String overlap = currentChunk.substring(
                            Math.max(0, currentChunk.length() - OVERLAP_SIZE)
                    );
                    currentChunk = new StringBuilder(overlap);
                } else {
                    currentChunk = new StringBuilder();
                }
            }

            // 添加段落
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
        }

        // 保存最后一个块
        if (currentChunk.length() > 0) {
            String lastChunk = currentChunk.toString().trim();
            // 合并过小的块
            if (!chunks.isEmpty() && estimateTokenSize(lastChunk) < MIN_CHUNK_SIZE) {
                chunks.set(chunks.size() - 1,
                        chunks.get(chunks.size() - 1) + "\n\n" + lastChunk);
            } else {
                chunks.add(lastChunk);
            }
        }

        log.debug("文本分块完成: 总计 {} 个分块", chunks.size());
        return chunks;
    }

    /**
     * 估算文本的 token 数量
     * 粗略估算: 1 token ≈ 0.5 个中文字符 ≈ 0.75 个英文字符
     */
    private int estimateTokenSize(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        // 中文: 2 字符 ≈ 1 token
        // 英文: 3 字符 ≈ 1 token
        return chineseChars / 2 + otherChars / 3;
    }
}
