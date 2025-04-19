package com.github.sahariardev.chaos;

import java.util.ArrayList;
import java.util.List;

public class ChaosConfig {

    private final ChaosType type;

    private final List<String> fields = new ArrayList<>();

    public ChaosConfig(ChaosType type) {
        this.type = type;
    }

    public void addField(String field) {
        fields.add(field);
    }

    public ChaosType getType() {
        return type;
    }

    public List<String> getFields() {
        return fields;
    }
}
