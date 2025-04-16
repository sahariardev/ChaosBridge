package com.github.sahariardev.chaos;

import java.util.function.Function;

public enum ChaosType {
    BANDWIDTH("BandwidthChaos", (chaosType) -> {
        ChaosConfig config = new ChaosConfig(chaosType);
        config.addField("bytePerSecond");
        return config;
    }),

    LATENCY("Latency", (chaosType) -> {
        ChaosConfig config = new ChaosConfig(chaosType);
        config.addField("latency");
        return config;
    }),

    EMPTY("EmptyChaos", true, null);

    private final String displayName;

    private final boolean forInternalUse;

    private final Function<ChaosType, ChaosConfig> configFunction;

    ChaosType(String displayName, boolean forInternalUse, Function<ChaosType, ChaosConfig> configFunction) {
        this.displayName = displayName;
        this.forInternalUse = forInternalUse;
        this.configFunction = configFunction;
    }


    ChaosType(String displayName, Function<ChaosType, ChaosConfig> configFunction) {
        this(displayName, false, configFunction);
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isForInternalUse() {
        return forInternalUse;
    }

    public Function<ChaosType, ChaosConfig> getconfigFunction() {
        return configFunction;
    }
}
