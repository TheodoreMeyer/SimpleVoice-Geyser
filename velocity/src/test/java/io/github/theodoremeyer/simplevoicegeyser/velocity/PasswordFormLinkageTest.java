package io.github.theodoremeyer.simplevoicegeyser.velocity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PasswordFormLinkageTest {

    @Test
    void passwordFormDoesNotLinkAgainstCumulusDirectly() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream(
                "io/github/theodoremeyer/simplevoicegeyser/velocity/geyser/PasswordForm.class")) {
            assertNotNull(in);
            String constants = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
            assertFalse(constants.contains("org/geysermc/cumulus"),
                    "Geyser and Floodgate each bundle Cumulus; a direct reference can bind to the wrong copy");
        }
    }
}
