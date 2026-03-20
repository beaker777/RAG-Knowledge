package com.beaker;

import com.beaker.response.Response;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * @Author beaker
 * @Date 2026/3/20 18:18
 * @Description RAG 服务 api
 */
public interface IRAGService {

    Response<List<String>> queryRagTagList();

    Response<String> uploadFile(String ragTag, List<MultipartFile> files);

    Response<String> analyzeGitRepository(String repoURL , String username, String token) throws Exception;
}
