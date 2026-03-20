package com.beaker.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author beaker
 * @Date 2026/3/20 18:19
 * @Description 响应类
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Response<T> {

    private String code;
    private String info;
    private T data;
}
