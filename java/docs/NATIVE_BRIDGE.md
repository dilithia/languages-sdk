# Java Native Bridge Direction

The Java SDK should consume the shared `native-core/` bridge through JNI/JNA.

That keeps the JVM layer aligned with `dilithia-core` while avoiding a second
crypto implementation.

Current integration path:

1. build `native-core` as a platform shared library
2. load it through JNA from `NativeCryptoBridge`
3. parse JSON bridge payloads into Java records
4. keep `DilithiaCryptoAdapter` stable while changing the underlying binding

Current status:

- mnemonic generation/validation and HD account recovery are wired
- wallet-file creation/recovery are wired through `native-core`
- sign and verify are wired through the same bridge
