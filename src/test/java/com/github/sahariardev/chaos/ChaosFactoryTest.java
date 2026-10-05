package com.github.sahariardev.chaos;

import com.github.sahariardev.common.Store;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The chaos store keeps numeric values as {@link Number} instances, while JSON/form input arrives
 * as strings. The factory must accept both. These tests guard against the regression where the
 * factory blindly cast the stored value to {@link String} and produced a {@link ClassCastException}
 * for every real proxy connection.
 */
class ChaosFactoryTest {

    private static final String KEY = "9999:localhost:9998";

    @AfterEach
    void cleanup() {
        Store.INSTANCE.remove(KEY);
    }

    static Stream<Arguments> storedConfigurations() {
        return Stream.of(
                Arguments.of("BANDWIDTH", "bytePerSecond", 1024, BandwidthChaos.class),
                Arguments.of("LATENCY", "latency", 2, LatencyChaos.class),
                Arguments.of("PACKET_LOSS", "packetLossRate", 0.25, PacketLossChaos.class)
        );
    }

    @ParameterizedTest(name = "builds {3} from a stored {0} configuration")
    @MethodSource("storedConfigurations")
    void buildsChaosFromNumberValues(String type, String field, Object value, Class<? extends Chaos> expected) throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("type", type);
        config.put("line", "upstream");
        config.put(field, value);

        Chaos chaos = ChaosFactory.buildChaos(config);

        assertInstanceOf(expected, chaos);
    }

    static Stream<Arguments> stringConfigurations() {
        return Stream.of(
                Arguments.of("BANDWIDTH", "bytePerSecond", "1024", BandwidthChaos.class),
                Arguments.of("LATENCY", "latency", "2", LatencyChaos.class),
                Arguments.of("PACKET_LOSS", "packetLossRate", "0.25", PacketLossChaos.class)
        );
    }

    @ParameterizedTest(name = "builds {3} from a string {0} configuration")
    @MethodSource("stringConfigurations")
    void buildsChaosFromStringValues(String type, String field, String value, Class<? extends Chaos> expected) throws IOException {
        Map<String, Object> config = new HashMap<>();
        config.put("type", type);
        config.put("line", "downstream");
        config.put(field, value);

        assertInstanceOf(expected, ChaosFactory.buildChaos(config));
    }

    @Test
    void buildsChaosFromConfigurationStoredThroughTheApi() throws IOException {
        Map<String, String> form = Map.of(
                "chaosType", "LATENCY",
                "line", "upstream",
                "latency", "1"
        );

        ChaosType.LATENCY.addChaos(form, KEY);
        Map<String, Object> stored = Store.INSTANCE.get(KEY).get(0);

        // This used to throw ClassCastException: Integer cannot be cast to String.
        assertInstanceOf(LatencyChaos.class, ChaosFactory.buildChaos(stored));
    }

    @Test
    void rejectsUnknownChaosType() {
        Map<String, Object> config = Map.of("type", "DOES_NOT_EXIST");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ChaosFactory.buildChaos(config));
        assertEquals(true, exception.getMessage().contains("DOES_NOT_EXIST"));
    }

    @Test
    void reportsMissingRequiredField() {
        Map<String, Object> config = new HashMap<>();
        config.put("type", "BANDWIDTH");
        config.put("line", "upstream");

        assertThrows(IllegalArgumentException.class, () -> ChaosFactory.buildChaos(config));
    }
}
