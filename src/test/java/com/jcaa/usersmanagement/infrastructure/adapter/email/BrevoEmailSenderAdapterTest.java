package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@DisplayName("BrevoEmailSenderAdapter")
class BrevoEmailSenderAdapterTest {

  private static final String BASE_URL = "https://api.brevo.test";
  private static final String SEND_URL = BASE_URL + "/v3/smtp/email";

  private final EmailDestinationModel destination =
      new EmailDestinationModel(
          "destino@example.com", "Destino", "Asunto de prueba", "<p>Hola</p>");

  private static BrevoConfig config(final int maxAttempts, final int minimumCalls) {
    return new BrevoConfig(
        BASE_URL,
        "xkeysib-test",
        "remitente@example.com",
        "Gestion de Usuarios",
        Duration.ofMillis(100),
        Duration.ofMillis(100),
        maxAttempts,
        Duration.ofMillis(1),
        1.0,
        50,
        minimumCalls,
        minimumCalls,
        Duration.ofSeconds(30));
  }

  private record Fixture(BrevoEmailSenderAdapter adapter, MockRestServiceServer server) {}

  private static Fixture fixture(final int maxAttempts, final int minimumCalls) {
    final RestClient.Builder builder =
        RestClient.builder().baseUrl(BASE_URL).defaultHeader("api-key", "xkeysib-test");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final BrevoEmailSenderAdapter adapter =
        new BrevoEmailSenderAdapter(config(maxAttempts, minimumCalls), builder.build());
    return new Fixture(adapter, server);
  }

  @Test
  @DisplayName("debe enviar la peticion con remitente, destinatario y contenido")
  void shouldSendRequestToBrevo() {
    final Fixture fixture = fixture(3, 10);
    fixture
        .server()
        .expect(once(), requestTo(SEND_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", "xkeysib-test"))
        .andExpect(jsonPath("$.sender.email").value("remitente@example.com"))
        .andExpect(jsonPath("$.sender.name").value("Gestion de Usuarios"))
        .andExpect(jsonPath("$.to[0].email").value("destino@example.com"))
        .andExpect(jsonPath("$.to[0].name").value("Destino"))
        .andExpect(jsonPath("$.subject").value("Asunto de prueba"))
        .andExpect(jsonPath("$.htmlContent").value("<p>Hola</p>"))
        .andExpect(jsonPath("$.headers.idempotencyKey").exists())
        .andRespond(withStatus(HttpStatus.CREATED));

    assertDoesNotThrow(() -> fixture.adapter().send(destination));
    fixture.server().verify();
  }

  @Test
  @DisplayName("debe reintentar ante un error 5xx y terminar con exito")
  void shouldRetryOnServerErrorAndSucceed() {
    final Fixture fixture = fixture(3, 10);
    fixture
        .server()
        .expect(once(), requestTo(SEND_URL))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    fixture.server().expect(once(), requestTo(SEND_URL)).andRespond(withSuccess());

    assertDoesNotThrow(() -> fixture.adapter().send(destination));
    fixture.server().verify();
  }

  @Test
  @DisplayName("debe fallar con el codigo HTTP cuando Brevo rechaza la peticion")
  void shouldFailWithoutRetryOnClientError() {
    final Fixture fixture = fixture(3, 10);
    fixture
        .server()
        .expect(once(), requestTo(SEND_URL))
        .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

    final EmailSenderException exception =
        assertThrows(EmailSenderException.class, () -> fixture.adapter().send(destination));

    assertTrue(exception.getMessage().contains("HTTP 401"));
    assertTrue(exception.getMessage().contains("destino@example.com"));
    fixture.server().verify();
  }

  @Test
  @DisplayName("debe agotar los reintentos ante errores 5xx persistentes")
  void shouldExhaustRetriesOnPersistentServerError() {
    final Fixture fixture = fixture(2, 10);
    fixture
        .server()
        .expect(times(2), requestTo(SEND_URL))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

    final EmailSenderException exception =
        assertThrows(EmailSenderException.class, () -> fixture.adapter().send(destination));

    assertTrue(exception.getMessage().contains("HTTP 502"));
    fixture.server().verify();
  }

  @Test
  @DisplayName("debe fallar cuando no hay conexion con Brevo")
  void shouldFailOnConnectionError() {
    final Fixture fixture = fixture(1, 10);
    fixture
        .server()
        .expect(once(), requestTo(SEND_URL))
        .andRespond(withException(new IOException("sin conexion")));

    final EmailSenderException exception =
        assertThrows(EmailSenderException.class, () -> fixture.adapter().send(destination));

    assertTrue(exception.getMessage().contains("ResourceAccessException"));
  }

  @Test
  @DisplayName("debe rechazar el envio cuando el circuit breaker esta abierto")
  void shouldRejectWhenCircuitIsOpen() {
    final Fixture fixture = fixture(1, 2);
    fixture
        .server()
        .expect(times(2), requestTo(SEND_URL))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    assertThrows(EmailSenderException.class, () -> fixture.adapter().send(destination));
    assertThrows(EmailSenderException.class, () -> fixture.adapter().send(destination));
    final EmailSenderException exception =
        assertThrows(EmailSenderException.class, () -> fixture.adapter().send(destination));

    assertTrue(exception.getMessage().contains("circuit breaker abierto"));
    fixture.server().verify();
  }

  @Test
  @DisplayName("debe clasificar correctamente los fallos transitorios")
  void shouldClassifyTransientFailures() {
    assertTrue(
        BrevoEmailSenderAdapter.isTransientFailure(new ResourceAccessException("timeout")));
    assertTrue(
        BrevoEmailSenderAdapter.isTransientFailure(
            HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "error", null, null, null)));
    assertTrue(
        BrevoEmailSenderAdapter.isTransientFailure(
            HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS, "limite", null, null, null)));
    assertTrue(
        BrevoEmailSenderAdapter.isTransientFailure(
            HttpClientErrorException.create(
                HttpStatus.REQUEST_TIMEOUT, "timeout", null, null, null)));
    assertFalse(
        BrevoEmailSenderAdapter.isTransientFailure(
            HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "malo", null, null, null)));
    assertFalse(BrevoEmailSenderAdapter.isTransientFailure(new IllegalStateException("otro")));
  }
}
