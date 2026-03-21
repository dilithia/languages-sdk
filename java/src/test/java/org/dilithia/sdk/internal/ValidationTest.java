package org.dilithia.sdk.internal;

import org.dilithia.sdk.exception.ValidationException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {

    @Nested
    class RequireNonBlankTests {

        @Test
        void acceptsValidString() {
            assertDoesNotThrow(() -> Validation.requireNonBlank("hello", "field"));
        }

        @Test
        void rejectsNull() {
            var ex = assertThrows(ValidationException.class,
                    () -> Validation.requireNonBlank(null, "myField"));
            assertTrue(ex.getMessage().contains("myField"));
        }

        @Test
        void rejectsEmptyString() {
            assertThrows(ValidationException.class,
                    () -> Validation.requireNonBlank("", "field"));
        }

        @Test
        void rejectsBlankString() {
            assertThrows(ValidationException.class,
                    () -> Validation.requireNonBlank("   ", "field"));
        }
    }

    @Nested
    class RequireNonNullTests {

        @Test
        void acceptsNonNullObject() {
            assertDoesNotThrow(() -> Validation.requireNonNull("value", "field"));
        }

        @Test
        void acceptsNonNullInteger() {
            assertDoesNotThrow(() -> Validation.requireNonNull(42, "field"));
        }

        @Test
        void rejectsNull() {
            var ex = assertThrows(ValidationException.class,
                    () -> Validation.requireNonNull(null, "myObj"));
            assertTrue(ex.getMessage().contains("myObj"));
            assertTrue(ex.getMessage().contains("must not be null"));
        }
    }
}
