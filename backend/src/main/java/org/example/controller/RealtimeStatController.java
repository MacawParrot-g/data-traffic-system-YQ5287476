package org.example.controller;

import org.example.annotation.SkipRateLimit;
import org.example.common.Result;
import org.example.service.RealtimeStatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/realtime")
public class RealtimeStatController {

    @Autowired
    private RealtimeStatService realtimeStatService;

    @GetMapping("/stats")
    @SkipRateLimit
    public Result stats(@RequestParam String recorder, @RequestParam String recordData) {
        return realtimeStatService.getRealtimeStats(recorder, recordData);
    }
}