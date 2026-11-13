package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BrevoConfig")
class BrevoConfigTest {

  private static BrevoConfig build(
      final String baseUrl, final String apiKey, final String fromAddress) {
    return new BrevoConfig(
        baseUrl,
        apiKey,
        fromAddress,
        "Gestion de Usuarios",
        Duration.ofSeconds(2),
        Duration.ofSeconds(5),
        3,
        Duration.ofMillis(500),
        2.0,
        50,
        10,
        5,
        Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("debe crearse con valores validos")
  void shouldCreateWithValidValues() {
    final BrevoConfig config =
        build("https://api.brevo.com", "xkeysib-valida", "remitente@example.com");

    assertEquals("https://api.brevo.com", config.baseUrl());
    assertEquals("xkeysib-valida", config.apiKey());
    assertEquals("remitente@example.com", config.fromAddress());
  }

  @Test
  @DisplayName("debe rechazar una url base vacia")
  void shouldRejectBlankBaseUrl() {
    final IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> build(" ", "xkeysib-valida", "remitente@example.com"));

    assertTrue(exception.getMessage().contains("brevo.base-url"));
  }

  @Test
  @DisplayName("debe rechazar una clave api nula")
  void shouldRejectNullApiKey() {
    assertThrows(
        IllegalArgumentException.class,
        () -> build("https://api.brevo.com", null, "remitente@example.com"));
  }

  @Test
  @DisplayName("debe rechazar un remitente vacio")
  void shouldRejectBlankFromAddress() {
    assertThrows(
        IllegalArgumentException.class,
        () -> build("https://api.brevo.com", "xkeysib-valida", ""));
  }

  @Test
  @DisplayName("debe rechazar una clave que no sea de la api rest")
  void shouldRejectSmtpKey() {
    final IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> build("https://api.brevo.com", "xsmtpsib-clave", "remitente@example.com"));

    assertTrue(exception.getMessage().contains("xkeysib-"));
  }
}
