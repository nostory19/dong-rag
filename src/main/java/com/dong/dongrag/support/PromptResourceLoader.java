package com.dong.dongrag.support;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 从 classpath 加载 Prompt 文本（可热替换资源文件而少改 Java）。
 */
@Component
public class PromptResourceLoader {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String loadOrDefault(String classpathLocation, String fallback) {
        return cache.computeIfAbsent(classpathLocation, loc -> {
            try {
                ClassPathResource res = new ClassPathResource(loc);
                if (!res.exists()) {
                    return fallback;
                }
                return StreamUtils.copyToString(res.getInputStream(), StandardCharsets.UTF_8).trim();
            } catch (IOException e) {
                return fallback;
            }
        });
    }
}
