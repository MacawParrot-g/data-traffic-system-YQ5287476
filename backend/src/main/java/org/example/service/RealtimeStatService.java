package org.example.service;

import org.example.common.Result;

public interface RealtimeStatService {
    Result getRealtimeStats(String recorder, String recordData);
}