package com.github.sahariardev.chaos;

import com.github.sahariardev.common.Constant;
import com.github.sahariardev.common.Store;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

public enum ChaosType {
    BANDWIDTH("BandwidthChaos", (chaosType) -> {
        ChaosConfig config = new ChaosConfig(chaosType);
        config.addField("bytePerSecond");
        return config;
    }, (dataMap, key) -> {
        int bytePerSecond = Integer.parseInt(dataMap.get("bytePerSecond"));

        Map<String, Object> chasoData = new HashMap<>();
        chasoData.put(Constant.TYPE, "BANDWIDTH");
        chasoData.put("bytePerSecond", bytePerSecond);
        chasoData.put(Constant.LINE, dataMap.get("line").toLowerCase());

        Store.INSTANCE.put(key, chasoData);
    }),

    LATENCY("Latency", (chaosType) -> {
        ChaosConfig config = new ChaosConfig(chaosType);
        config.addField("latency");
        return config;
    }, (dataMap, key) -> {

        int latency = Integer.parseInt(dataMap.get("latency"));

        Map<String, Object> chasoData = new HashMap<>();
        chasoData.put(Constant.TYPE, "LATENCY");
        chasoData.put("latency", latency);
        chasoData.put(Constant.LINE, dataMap.get("line").toLowerCase());

        Store.INSTANCE.put(key, chasoData);

    }),

    PACKET_LOSS("Packet Loss", (chaosType) -> {
        ChaosConfig config = new ChaosConfig(chaosType);
        config.addField("packetLossRate");
        return config;
    }, (dataMap, key) -> {

        double packetLossRate = Double.parseDouble(dataMap.get("packetLossRate"));

        Map<String, Object> chasoData = new HashMap<>();
        chasoData.put(Constant.TYPE, "PACKET_LOSS");
        chasoData.put("packetLossRate", packetLossRate);
        chasoData.put(Constant.LINE, dataMap.get("line").toLowerCase());

        Store.INSTANCE.put(key, chasoData);
    }),

    EMPTY("EmptyChaos", true, null, null);

    private final String displayName;

    private final boolean forInternalUse;

    private final Function<ChaosType, ChaosConfig> configFunction;

    private final BiConsumer<Map<String, String>, String> addChaosConsumer;

    ChaosType(String displayName, boolean forInternalUse, Function<ChaosType, ChaosConfig> configFunction, BiConsumer<Map<String, String>, String> addChaosConsumer) {
        this.displayName = displayName;
        this.forInternalUse = forInternalUse;
        this.configFunction = configFunction;
        this.addChaosConsumer = addChaosConsumer;
    }

    ChaosType(String displayName, Function<ChaosType, ChaosConfig> configFunction, BiConsumer<Map<String, String>, String> addChaosConsumer) {
        this(displayName, false, configFunction, addChaosConsumer);
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isForInternalUse() {
        return forInternalUse;
    }

    public ChaosConfig getConfig() {
        return configFunction.apply(this);
    }

    public void addChaos(Map<String, String> map, String key) {
        addChaosConsumer.accept(map, key);
    }
}
