package com.github.sahariardev.chaos;

import com.github.sahariardev.common.Constant;

import java.io.IOException;
import java.util.Map;

public class ChaosFactory {

    public static Chaos buildChaos(Map<String, Object> chaosConfiguration) throws IOException {
        if (chaosConfiguration.get(Constant.TYPE).equals(ChaosType.BANDWIDTH.name())) {
            return new BandwidthChaos(Integer.parseInt((String) chaosConfiguration.get("bytePerSecond")));
        }

        if (chaosConfiguration.get(Constant.TYPE).equals(ChaosType.LATENCY.name())) {
            return new BandwidthChaos(Integer.parseInt((String) chaosConfiguration.get("latency")));
        }

        throw new IllegalStateException();
    }

}
