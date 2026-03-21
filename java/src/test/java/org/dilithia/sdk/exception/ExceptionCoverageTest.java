package org.dilithia.sdk.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests covering exception constructors with cause parameters
 * to reach the remaining uncovered lines in exception classes.
 */
class ExceptionCoverageTest {

    @Test
    void cryptoExceptionWithCause() {
        var cause = new RuntimeException("inner");
        var ex = new CryptoException("crypto failed", cause);
        assertEquals("crypto failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertInstanceOf(DilithiaException.class, ex);
    }

    @Test
    void validationExceptionWithCause() {
        var cause = new RuntimeException("inner");
        var ex = new ValidationException("invalid", cause);
        assertEquals("invalid", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertInstanceOf(DilithiaException.class, ex);
    }

    @Test
    void timeoutExceptionWithMessageAndCause() {
        var cause = new RuntimeException("timed out");
        var ex = new TimeoutException("custom message", cause);
        assertEquals("custom message", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertInstanceOf(DilithiaException.class, ex);
    }

    @Test
    void serializationExceptionWithCause() {
        var cause = new RuntimeException("parse error");
        var ex = new SerializationException("bad json", cause);
        assertEquals("bad json", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void dilithiaExceptionWithCause() {
        var cause = new RuntimeException("root cause");
        var ex = new DilithiaException("failed", cause);
        assertEquals("failed", ex.getMessage());
        assertEquals(cause, ex.getCause());
    }
}
