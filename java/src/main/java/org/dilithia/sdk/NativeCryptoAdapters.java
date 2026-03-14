package org.dilithia.sdk;

import java.lang.reflect.Constructor;
import java.util.Optional;

public final class NativeCryptoAdapters {
    private NativeCryptoAdapters() {}

    public static Optional<DilithiaCryptoAdapter> load() {
        try {
            Class<?> clazz = Class.forName("org.dilithia.sdk.crypto.NativeCryptoBridge");
            if (!DilithiaCryptoAdapter.class.isAssignableFrom(clazz)) {
                return Optional.empty();
            }
            Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            return Optional.of((DilithiaCryptoAdapter) constructor.newInstance());
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return Optional.empty();
        }
    }
}
