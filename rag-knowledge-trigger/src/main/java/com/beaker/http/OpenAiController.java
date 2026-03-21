package com.beaker.http;

import com.beaker.IAiService;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author beaker
 * @Date 2026/3/21 12:22
 * @Description 使用 OpenAi 实现 api
 */
@RestController
@CrossOrigin("*")
@RequestMapping("api/v1/openai/")
public class OpenAiController implements IAiService {

    @Resource
    private OpenAiChatClient openAiChatClient;
    @Resource
    private PgVectorStore pgVectorStore;

    @GetMapping(value = "generate")
    @Override
    public ChatResponse generate(String model, String message) {
        return openAiChatClient.call(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    @GetMapping(value = "generate_stream")
    @Override
    public Flux<ChatResponse> generateStream(String model, String message) {
        return openAiChatClient.stream(new Prompt(
                message,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }

    @GetMapping(value = "generate_stream_rag")
    @Override
    public Flux<ChatResponse> generateStreamRag(String model, String ragTag, String message) {
        // 全局提示词
        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                Another thing you need to note is that your reply must be in Chinese!
                DOCUMENTS:
                    {documents}
                """;

        // 根据 message 发送 request, 只要 Top5 而且要满足过滤条件
        SearchRequest request = SearchRequest.query(message)
                .withTopK(5)
                .withFilterExpression("knowledge == '" + ragTag + "'");

        // 在知识库中搜索, 将搜索到的信息拼接成一个大字符串
        List<Document> documents = pgVectorStore.similaritySearch(request);
        String collectedDocuments = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // 将字符串填充到 system_message 的 {document}
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", collectedDocuments));

        // 消息数组
        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        // LLM 返回结果
        return openAiChatClient.stream(new Prompt(
                messages,
                OpenAiChatOptions.builder()
                        .withModel(model)
                        .build()
        ));
    }
}
