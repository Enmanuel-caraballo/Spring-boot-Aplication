package com.jcaa.usersmanagement.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

@DisplayName("BrevoSpringConfig")
class BrevoSpringConfigTest {

  private final BrevoSpringConfig springConfig = new BrevoSpringConfig();

  private BrevoConfig buildConfig() {
    return springConfig.brevoConfig(
        "https://api.brevo.com",
        "xkeysib-test",
        "remitente@example.com",
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
  @DisplayName("debe construir la configuracion de Brevo con los valores recibidos")
  void shouldBuildBrevoConfig() {
    final BrevoConfig config = buildConfig();

    assertEquals("https://api.brevo.com", config.baseUrl());
    assertEquals("xkeysib-test", config.apiKey());
    assertEquals(3, config.retryMaxAttempts());
    assertEquals(Duration.ofSeconds(30), config.circuitOpenDuration());
  }

  @Test
  @DisplayName("debe construir el cliente REST de Brevo")
  void shouldBuildRestClient() {
    final RestClient restClient = springConfig.brevoRestClient(buildConfig());

    assertNotNull(restClient);
  }

  @Test
  @DisplayName("la huella de la clave debe ser corta y no revelar la clave")
  void shouldProduceShortFingerprint() {
    final String fingerprint = BrevoSpringConfig.fingerprint("xkeysib-test");

    assertEquals(12, fingerprint.length());
    assertNotEquals("xkeysib-test", fingerprint);
    assertEquals(fingerprint, BrevoSpringConfig.fingerprint("xkeysib-test"));
  }
}
