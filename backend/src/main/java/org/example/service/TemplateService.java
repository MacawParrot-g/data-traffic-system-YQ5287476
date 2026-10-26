package org.example.service;

import org.example.common.Result;

public interface TemplateService {
    Result getOptionsByType(String type);
    Result addTemplate(String content, String type, String writer);
    Result updateTemplate(String hash, String content, String type);
    Result deleteTemplate(String hash);
    Result searchTemplates(String type, String keyword, String writer, int page, int size);
    Result manualWarmUp(Long ttlSeconds);
    void seedIfEmpty();
}