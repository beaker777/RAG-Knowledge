package com.beaker.http;

import com.beaker.IRAGService;
import com.beaker.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * @Author beaker
 * @Date 2026/3/20 18:22
 * @Description rag api 实现
 */
@RestController
@CrossOrigin("*")
@RequestMapping("api/v1/rag/")
@Slf4j
public class RAGController implements IRAGService {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Resource
    private RedissonClient redissonClient;

    @GetMapping(value = "query_rag_tag_list")
    @Override
    public Response<List<String>> queryRagTagList() {
        RList<String> ragTags = redissonClient.getList("ragTag");

        return Response.<List<String>>builder()
                .code("0000")
                .info("success")
                .data(ragTags)
                .build();
    }

    @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
    @Override
    public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
        for (MultipartFile file : files) {
            // 使用 tika 读取文件
            TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());

            // 将文件打上对应知识库的标记
            List<Document> documents = documentReader.get();
            documents.forEach(document -> document.getMetadata().put("knowledge", ragTag));

            // 将大文件切分
            List<Document> splitDocuments = tokenTextSplitter.apply(documents);

            // 向量化存储到数据库中
            pgVectorStore.accept(splitDocuments);

            // 检查 Redis 是否含有该 tag, 没有则添加
            RList<String> ragTags = redissonClient.getList("ragTag");
            if (!ragTags.contains(ragTag)) {
                ragTags.add(ragTag);
            }
        }

        log.info("上传文件成功!");
        return Response.<String>builder().code("0000").info("success").build();
    }
}
