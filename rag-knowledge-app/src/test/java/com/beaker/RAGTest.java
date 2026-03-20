package com.beaker;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author beaker
 * @Date 2026/3/20 16:38
 * @Description 测试 RAG
 */
@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        TikaDocumentReader tikaDocumentReader = new TikaDocumentReader("./data/file.txt");

        List<Document> documents = tikaDocumentReader.get();
        documents.forEach(document -> document.getMetadata().put("knowledge", "测试用知识库"));

        List<Document> splitDocuments = tokenTextSplitter.apply(documents);

        pgVectorStore.accept(splitDocuments);

        log.info("数据上传完成!");
    }

    @Test
    public void chat() {
        String message = "介绍一下我就读的这所大学";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 根据 message 发送 request, 只要 Top5 而且要满足过滤条件
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '测试用知识库'");

        // 在知识库中搜索, 将搜索到的信息拼接成一个大字符串
        List<Document> documents = pgVectorStore.similaritySearch(request);
        String collectedDocuments = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // 将字符串填充到 system_message 的 document
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", collectedDocuments));

        // 消息数组
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        // LLM 返回结果
        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:7b")));

        log.info("测试结果: {}", JSON.toJSONString(chatResponse));
    }
}
