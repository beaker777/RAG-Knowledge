package com.beaker.http;

import com.beaker.IAiService;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import javax.annotation.Resource;

/**
 * @Author beaker
 * @Date 2026/3/20 13:17
 * @Description Ollama 实现 ai 服务
 */
@RestController
@CrossOrigin("*")
@RequestMapping("api/v1/ollama/")
public class OllamaController implements IAiService {

    @Resource
    private OllamaChatClient ollamaChatClient;

    /**
     * curl http://localhost:8090/api/v1/ollama/generate?model=deepseek-r1:7b&message=1+1
     */
    @GetMapping(value = "generate")
    @Override
    public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
        return ollamaChatClient.call(new Prompt(message, OllamaOptions.create().withModel(model)));
    }

    /**
     * curl http://localhost:8090/api/v1/ollama/generate_stream?model=deepseek-r1:7b&message=1+1
     */
    @GetMapping(value = "generate_stream")
    @Override
    public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
        return ollamaChatClient.stream(new Prompt(message, OllamaOptions.create().withModel(model)));
    }
}
