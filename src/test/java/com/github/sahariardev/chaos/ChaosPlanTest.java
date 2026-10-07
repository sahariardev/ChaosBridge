package com.github.sahariardev.chaos;

import com.github.sahariardev.common.Constant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChaosPlanTest {

    private static Map<String, Object> profile(String type, String line) {
        Map<String, Object> map = new HashMap<>();
        map.put(Constant.TYPE, type);
        map.put(Constant.LINE, line);
        return map;
    }

    @Test
    void emptyPlanWhenNoProfiles() {
        ChaosPlan plan = ChaosPlan.from(List.of(), Constant.UPSTREAM);

        assertTrue(plan.isEmpty());
        assertFalse(plan.packetLossEnabled());
        assertEquals(0, plan.latencySeconds());
        assertNull(plan.bytesPerSecond());
    }

    @Test
    void onlyIncludesProfilesForTheRequestedDirection() {
        Map<String, Object> upstream = profile(ChaosType.LATENCY.name(), Constant.UPSTREAM);
        upstream.put("latency", 3);
        Map<String, Object> downstream = profile(ChaosType.LATENCY.name(), Constant.DOWNSTREAM);
        downstream.put("latency", 9);

        ChaosPlan plan = ChaosPlan.from(List.of(upstream, downstream), Constant.UPSTREAM);

        assertEquals(3, plan.latencySeconds());
    }

    @Test
    void parsesNumericValuesStoredAsNumbersAndStrings() {
        Map<String, Object> bandwidth = profile(ChaosType.BANDWIDTH.name(), Constant.DOWNSTREAM);
        bandwidth.put("bytePerSecond", 2048); // Integer
        Map<String, Object> packetLoss = profile(ChaosType.PACKET_LOSS.name(), Constant.DOWNSTREAM);
        packetLoss.put("packetLossRate", "0.4"); // String

        ChaosPlan plan = ChaosPlan.from(List.of(bandwidth, packetLoss), Constant.DOWNSTREAM);

        assertEquals(2048, plan.bytesPerSecond());
        assertTrue(plan.packetLossEnabled());
        assertEquals(0.4, plan.packetLossRate(), 0.0001);
    }

    @Test
    void sumsLatencyAndKeepsTheStrictestBandwidth() {
        List<Map<String, Object>> profiles = new ArrayList<>();

        Map<String, Object> latencyA = profile(ChaosType.LATENCY.name(), Constant.UPSTREAM);
        latencyA.put("latency", 1);
        Map<String, Object> latencyB = profile(ChaosType.LATENCY.name(), Constant.UPSTREAM);
        latencyB.put("latency", 2);
        Map<String, Object> bandwidthA = profile(ChaosType.BANDWIDTH.name(), Constant.UPSTREAM);
        bandwidthA.put("bytePerSecond", 8192);
        Map<String, Object> bandwidthB = profile(ChaosType.BANDWIDTH.name(), Constant.UPSTREAM);
        bandwidthB.put("bytePerSecond", 1024);
        profiles.add(latencyA);
        profiles.add(latencyB);
        profiles.add(bandwidthA);
        profiles.add(bandwidthB);

        ChaosPlan plan = ChaosPlan.from(profiles, Constant.UPSTREAM);

        assertEquals(3, plan.latencySeconds());
        assertEquals(1024, plan.bytesPerSecond());
        assertFalse(plan.isEmpty());
    }
}
