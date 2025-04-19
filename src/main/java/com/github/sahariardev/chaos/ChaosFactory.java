package com.github.sahariardev.chaos;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.sahariardev.common.Constant;

import java.io.IOException;
import java.util.Map;

public class ChaosFactory {

    public static Chaos buildChaos(Map<String, Object> chaosConfiguration) throws IOException {
        if (chaosConfiguration.get(Constant.TYPE).equals("bandwidthChaos")) {
            return new BandwidthChaos(Integer.parseInt((String) chaosConfiguration.get("bytePerSecond")));
        }

        throw new IllegalStateException();
    }

}
