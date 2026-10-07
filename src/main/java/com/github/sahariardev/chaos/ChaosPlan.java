package com.github.sahariardev.chaos;

import com.github.sahariardev.common.Constant;

import java.util.List;
import java.util.Map;

/**
 * A flattened, immutable view of the chaos profiles active for one direction of a proxy. It is
 * rebuilt from the store on demand so profiles added or removed at runtime take effect immediately,
 * including on connections that are already open.
 *
 * @param packetLossEnabled whether a packet-loss rule is active
 * @param packetLossRate    drop probability (0.0 - 1.0)
 * @param latencySeconds    total added latency per chunk, in seconds
 * @param bytesPerSecond    bandwidth limit in bytes per second, or {@code null} when unlimited
 */
public record ChaosPlan(boolean packetLossEnabled, double packetLossRate, int latencySeconds, Integer bytesPerSecond) {

    public static final ChaosPlan EMPTY = new ChaosPlan(false, 0.0, 0, null);

    public boolean isEmpty() {
        return !packetLossEnabled && latencySeconds <= 0 && bytesPerSecond == null;
    }

    public static ChaosPlan from(List<Map<String, Object>> configs, String line) {
        boolean packetLossEnabled = false;
        double packetLossRate = 0.0;
        int latencySeconds = 0;
        Integer bytesPerSecond = null;

        if (configs != null) {
            for (Map<String, Object> config : configs) {
                Object configLine = config.get(Constant.LINE);
                if (configLine == null || !line.equalsIgnoreCase(String.valueOf(configLine))) {
                    continue;
                }

                Object type = config.get(Constant.TYPE);
                if (ChaosType.PACKET_LOSS.name().equals(type)) {
                    packetLossEnabled = true;
                    packetLossRate = toDouble(config.get("packetLossRate"));
                } else if (ChaosType.LATENCY.name().equals(type)) {
                    latencySeconds += toInt(config.get("latency"));
                } else if (ChaosType.BANDWIDTH.name().equals(type)) {
                    int value = toInt(config.get("bytePerSecond"));
                    bytesPerSecond = bytesPerSecond == null ? value : Math.min(bytesPerSecond, value);
                }
            }
        }

        return new ChaosPlan(packetLossEnabled, packetLossRate, latencySeconds, bytesPerSecond);
    }

    private static int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value).trim());
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value).trim());
    }
}
