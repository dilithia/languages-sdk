package org.dilithia.sdk;

import java.util.Map;

@FunctionalInterface
public interface DilithiaSigner {
    Map<String, Object> signCanonicalPayload(Map<String, Object> canonicalPayload);
}
