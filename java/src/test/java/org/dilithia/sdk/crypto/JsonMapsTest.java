package org.dilithia.sdk.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonMapsTest {
    @Test
    void stringifyRoundTripsSimpleObject() {
        Map<String, Object> walletFile = new LinkedHashMap<>();
        walletFile.put("version", 1);
        walletFile.put("address", "dili1abc");
        walletFile.put("account_index", 2);

        String json = JsonMaps.stringify(walletFile);
        Map<String, Object> parsed = JsonMaps.parse(json);

        assertEquals(1L, parsed.get("version"));
        assertEquals("dili1abc", parsed.get("address"));
        assertEquals(2L, parsed.get("account_index"));
    }

    @Test
    void parseNestedObject() {
        Map<String, Object> parsed = JsonMaps.parse("""
                {
                  "ok": true,
                  "value": {
                    "address": "dili1abc",
                    "wallet_file": {
                      "version": 1
                    }
                  }
                }
                """);

        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) parsed.get("value");
        @SuppressWarnings("unchecked")
        Map<String, Object> walletFile = (Map<String, Object>) value.get("wallet_file");

        assertEquals("dili1abc", value.get("address"));
        assertEquals(1L, walletFile.get("version"));
    }
}
