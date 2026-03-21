package com.beaker.http;

import com.beaker.IRAGService;
import com.beaker.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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

    @PostMapping(value = "analyze_git_repository")
    @Override
    public Response<String> analyzeGitRepository(@RequestParam String repoURL, @RequestParam String username, @RequestParam String token) throws Exception {
        String localPath = "./cloned-repo";
        String projectName = extractProjectName(repoURL);

        log.info("克隆路径: {}", new File(localPath).getAbsolutePath());

        // 先清空文件夹
        FileUtils.deleteDirectory(new File(localPath));

        // 使用 git pull 仓库
        Git git = Git.cloneRepository()
                .setURI(repoURL)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
                .call();

        // 依次遍历克隆的所有文件, 并向量化存储到知识库
        Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<>(){
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("遍历项目: {}, 解析文件: {}", projectName, file.getFileName());
                try {
                    TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file.getFileName()));

                    List<Document> documents = reader.get();
                    documents.forEach(document -> document.getMetadata().put("knowledge", projectName));

                    List<Document> splitDocument = tokenTextSplitter.apply(documents);

                    pgVectorStore.accept(splitDocument);
                } catch (Exception e) {
                    log.error("上传知识库失败, 当前文件: {}", file.getFileName());
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                log.info("遍历项目: {}, 解析文件失败: {}", projectName, file.getFileName());
                return FileVisitResult.CONTINUE;
            }
        });

        // 遍历结束, 清空本地克隆文件
        FileUtils.deleteDirectory(new File(localPath));

        // 知识库若不存在当前项目则加入
        RList<String> ragTags = redissonClient.getList("ragTag");
        if (!ragTags.contains(projectName)) {
            ragTags.add(projectName);
        }

        git.close();

        log.info("遍历解析完成!");

        return Response.<String>builder().code("0000").info("success").build();
    }

    private String extractProjectName(String repoURL) {
        String[] parts = repoURL.split("/");
        String projectNameWithGit = parts[parts.length - 1];

        return projectNameWithGit.replace(".git", "");
    }
}
