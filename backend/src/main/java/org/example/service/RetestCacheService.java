package org.example.service;

import org.example.common.Result;

public interface RetestCacheService {

    int warmUp();

    Result manualWarmUp();

    Result randomBundle();

    Result listCache();

    Result addBundle(String bundleId, String grade);

    Result updateBundle(String bundleId, String grade);

    Result deleteBundle(String bundleId);

    Result clearCache();
}
