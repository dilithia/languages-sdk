package org.dilithia.sdk.crypto;

import java.lang.reflect.Constructor;
import java.util.Optional;

/**
 * Reflective loader for the native crypto bridge.
 *
 * <p>Returns {@link Optional#empty()} when the native library is
 * unavailable (e.g. missing JNA or env var not set).</p>
 */
public final class NativeCryptoAdapters {
    private NativeCryptoAdapters() {}

    /**
     * Attempts to load the {@link NativeCryptoBridge}.
     *
     * @return the adapter if available, empty otherwise
     */
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
