package com.example.app.security;

import com.example.app.controller.RestOrderController;
import com.example.app.domain.Order;
import com.example.app.service.OrderService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@WebMvcTest(controllers = RestOrderController.class)
@Import(SecurityConfiguration.class)
@EnableWebSecurity
class SecurityErrorDispatchTest {
  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  @Test
  void allowsInternalErrorDispatchAndPreservesOriginalStatus() throws Exception {
    mockMvc.perform(get("/error").with(request -> {
          request.setDispatcherType(DispatcherType.ERROR);
          request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, HttpStatus.BAD_REQUEST.value());
          request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/orders");
          request.setAttribute(RequestDispatcher.ERROR_MESSAGE, "customer does not exist");
          return request;
        }))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deniesDirectRequestToErrorEndpoint() throws Exception {
    stubWriterToken();

    mockMvc.perform(get("/error").header("Authorization", "Bearer writer-token"))
        .andExpect(status().isForbidden());
  }

  @Test
  void returnsBadRequestWhenOrderReferencesUnknownCustomer() throws Exception {
    stubWriterToken();
    when(orderService.createOrder(any(Order.class))).thenThrow(
        new ResponseStatusException(HttpStatus.BAD_REQUEST, "customer does not exist"));

    mockMvc.perform(post("/api/orders")
            .header("Authorization", "Bearer writer-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customer\":\"Missing Customer\",\"amount\":10.50}"))
        .andExpect(status().isBadRequest());
  }

  private void stubWriterToken() {
    when(jwtDecoder.decode("writer-token")).thenReturn(Jwt.withTokenValue("writer-token")
        .header("alg", "RS256")
        .claim("sub", "test-user")
        .claim("realm_access", Map.of("roles", List.of("ORDER_WRITE")))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build());
  }
}
