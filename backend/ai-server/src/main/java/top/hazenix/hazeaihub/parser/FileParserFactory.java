package top.hazenix.hazeaihub.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件解析器工厂
 */
@Component
@RequiredArgsConstructor
public class FileParserFactory {

    /**
     * 类上有 @RequiredArgsConstructor 注解，Spring 会自动把所有 FileParser 实现类通过构造器注入进来
     */
    private final List<FileParser> parsers;

    /**
     * key: 文件类型; value: 解析器
     */
    private Map<String, FileParser> parserMap;

    /**
     * 初始化解析器映射
     */
    private Map<String, FileParser> getParserMap() {
        if (parserMap != null) {
            return parserMap;
        }

        // key: 文件类型, value: 对应解析器
        // 如果重复,保留已有的解析器
        parserMap = new java.util.HashMap<>();
        for (FileParser parser : parsers) {
            parserMap.putIfAbsent(parser.getFileType(), parser);
        }

        return parserMap;
    }

    /**
     * 获取解析器
     * @param fileType 文件类型
     * @return 对应的解析器，如果不存在返回 null
     */
    public FileParser getParser(String fileType) {
        return getParserMap().get(fileType.toUpperCase());
    }

    /**
     * 检查是否支持该文件类型
     */
    public boolean supports(String fileType) {
        return getParserMap().containsKey(fileType.toUpperCase());
    }
}
