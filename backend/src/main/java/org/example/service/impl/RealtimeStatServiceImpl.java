package org.example.service.impl;

import org.example.common.Result;
import org.example.mapper.GeneranMapper;
import org.example.service.RealtimeStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class RealtimeStatServiceImpl implements RealtimeStatService {


    @Autowired
    private RestTemplate restTemplate;

    @Value("${upstream.base-url}")
    private String upstreamBaseUrl;

    private String getInventoryUrl() {
        return upstreamBaseUrl + "/ad/play/task/count";
    }

    @Autowired
    private GeneranMapper generanMapper;

    @Override
    public Result getRealtimeStats(String recorder, String recordData) {
        Object inventory = fetchInventorySilently();
        int todayCount = fetchTodayCountSilently(recorder, recordData);
        Map<String, Object> data = new HashMap<>();
        data.put("inventory", inventory);
        data.put("todayCount", todayCount);
        return Result.success("实时统计获取成功", data);
    }

    @SuppressWarnings("unchecked")
    private Object fetchInventorySilently() {
        try {
            Map<String, Object> resp = restTemplate.getForObject(getInventoryUrl(), Map.class);
            if (resp != null && Boolean.TRUE.equals(resp.get("success"))) {
                return resp.get("data");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int fetchTodayCountSilently(String recorder, String recordData) {
        try {
            return generanMapper.countByRecorderAndDate(recorder, normalizeToDateString(recordData));
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String normalizeToDateString(String input) {
        if (input == null || input.isEmpty()) return input;
        if (input.contains("T")) {
            return input.substring(0, 10);
        }
        if (input.contains("/")) {
            String[] parts = input.split("/");
            if (parts.length == 3) {
                return String.format("%s-%02d-%02d",
                        parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            }
        }
        return input;
    }
}