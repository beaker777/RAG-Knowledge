package com.beaker;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @Author beaker
 * @Date 2026/3/20 20:21
 * @Description 用于测试 Git 相关方法
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class JGitTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void test() throws IOException, GitAPIException {
        String repoURL = "https://github.com/beaker777/WHUT-Lab.git";
        String username = "beaker777";
        String password = "ghp_tTBloZtClk(ur token)sE020seXEX";

        String localPath = "./clone-repo";
        log.info("克隆路径: {}", new File(localPath).getAbsolutePath());

        FileUtils.deleteDirectory(new File(localPath));

        Git git = Git.cloneRepository()
                .setURI(repoURL)
                .setDirectory(new File(localPath))
                .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, password))
                .call();

        git.close();
    }

    @Test
    public void test_file() throws IOException {
        Files.walkFileTree(Paths.get("./clone-repo"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.info("文件路径: {}", file.toString());

//                // 使用 Tika 读取文件, 并转换为 document
//                PathResource resource = new PathResource(file);
//                TikaDocumentReader reader = new TikaDocumentReader(resource);
//
//                List<Document> documents = reader.get();
//                documents.forEach(document -> document.getMetadata().put("knowledge", "WHUT-Lab"));
//
//                List<Document> splitDocuments = tokenTextSplitter.apply(documents);
//
//                pgVectorStore.accept(splitDocuments);

                return FileVisitResult.CONTINUE;
            }
        });
    }
}
