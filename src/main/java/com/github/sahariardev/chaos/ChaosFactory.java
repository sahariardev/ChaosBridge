package com.github.sahariardev.chaos;

import com.github.sahariardev.common.Constant;
import com.github.sahariardev.metrics.ProxyMetrics;

import java.io.IOException;
import java.util.Map;

public class ChaosFactory {

    private ChaosFactory() {
    }

    public static Chaos buildChaos(Map<String, Object> chaosConfiguration) throws IOException {
        return buildChaos(chaosConfiguration, ProxyMetrics.noop());
    }

    public static Chaos buildChaos(Map<String, Object> chaosConfiguration, ProxyMetrics metrics) throws IOException {
        Object type = chaosConfiguration.get(Constant.TYPE);

        if (ChaosType.BANDWIDTH.name().equals(type)) {
            return new BandwidthChaos(toInt(chaosConfiguration.get("bytePerSecond"), "bytePerSecond"), metrics);
        }

        if (ChaosType.LATENCY.name().equals(type)) {
            return new LatencyChaos(toInt(chaosConfiguration.get("latency"), "latency"), metrics);
        }

        if (ChaosType.PACKET_LOSS.name().equals(type)) {
            return new PacketLossChaos(toDouble(chaosConfiguration.get("packetLossRate"), "packetLossRate"), metrics);
        }

        throw new IllegalArgumentException("Unsupported chaos type: " + type);
    }

    /**
     * The store keeps numeric chaos values as {@link Number} (Integer/Double), while form/JSON input
     * arrives as {@link String}. Accept both representations instead of blindly casting to String.
     */
    private static int toInt(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required chaos field: " + field);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static double toDouble(Object value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required chaos field: " + field);
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }
}
