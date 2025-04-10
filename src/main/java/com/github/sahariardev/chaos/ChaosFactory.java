package com.github.sahariardev.chaos;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.sahariardev.common.Constant;

import java.io.IOException;

public class ChaosFactory {

    public static Chaos buildChaos(ObjectNode chaosConfiguration) throws IOException {
        if (chaosConfiguration.get(Constant.TYPE).asText().equals("bandwidthChaos")) {
            return new BandwidthChaos(Integer.parseInt(chaosConfiguration.get("bytePerSecond").asText()));
        }

        throw new IllegalStateException();
    }

}
