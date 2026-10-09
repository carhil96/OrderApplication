package com.example.app.security;

import com.example.app.repository.OrderRepository;
import com.example.app.domain.Order;
import com.example.app.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://issuer.example.test/jwks"
})
@AutoConfigureMockMvc
class SecurityConfigurationTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private OrderService orderService;

  @MockitoBean
  private OrderRepository orderRepository;

  @MockitoBean
  private JwtDecoder jwtDecoder;

  /** Confirma que una consulta REST sin token se rechaza. */
  @Test
  void rejectsOrderRequestsWithoutToken() throws Exception {
    mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
  }

  /** Confirma que un token inválido se rechaza. */
  @Test
  void rejectsInvalidToken() throws Exception {
    when(jwtDecoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));

    mockMvc.perform(get("/api/orders").header("Authorization", "Bearer invalid-token"))
        .andExpect(status().isUnauthorized());
  }

  /** Confirma que un usuario con ORDER_READ puede consultar pedidos. */
  @Test
  void acceptsValidTokenForOrderEndpoint() throws Exception {
    when(orderRepository.findAll()).thenReturn(List.of());
    stubToken("read-token", "ORDER_READ");

    mockMvc.perform(get("/api/orders").header("Authorization", "Bearer read-token"))
        .andExpect(status().isOk());
  }

  /** Confirma que un usuario sin ORDER_READ no puede consultar pedidos. */
  @Test
  void rejectsOrderReadWhenTokenHasNoReadRole() throws Exception {
    stubToken("write-token", "ORDER_WRITE");

    mockMvc.perform(get("/api/orders").header("Authorization", "Bearer write-token"))
        .andExpect(status().isForbidden());
  }

  /** Confirma que un usuario de solo lectura no puede crear pedidos por REST. */
  @Test
  void rejectsOrderCreationForReadOnlyRole() throws Exception {
    stubToken("read-token", "ORDER_READ");

    mockMvc.perform(post("/api/orders")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customer\":\"Ada\",\"amount\":10.50}"))
        .andExpect(status().isForbidden());
  }

  /** Confirma que un usuario con ORDER_WRITE puede crear pedidos por REST. */
  @Test
  void allowsOrderCreationForWriteRole() throws Exception {
    stubToken("write-token", "ORDER_WRITE");
    when(orderService.createOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(post("/api/orders")
            .header("Authorization", "Bearer write-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customer\":\"Ada\",\"amount\":10.50}"))
        .andExpect(status().isOk());
  }

  /** Confirma que un usuario con ORDER_READ puede consultar pedidos por GraphQL. */
  @Test
  void allowsGraphQlOrderQueryForReadRole() throws Exception {
    stubToken("read-token", "ORDER_READ");
    when(orderRepository.findAll()).thenReturn(List.of());

    mockMvc.perform(post("/graphql")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"{ orders { customer } }\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  /** Confirma que un usuario de solo lectura no puede crear pedidos por GraphQL. */
  @Test
  void rejectsGraphQlOrderCreationForReadOnlyRole() throws Exception {
    stubToken("read-token", "ORDER_READ");

    mockMvc.perform(post("/graphql")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"mutation { createOrder(customer: \\\"Ada\\\", amount: 10.50) { customer } }\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors").exists());
  }

  /** Confirma que un usuario con ORDER_WRITE puede crear pedidos por GraphQL. */
  @Test
  void allowsGraphQlOrderCreationForWriteRole() throws Exception {
    stubToken("write-token", "ORDER_WRITE");
    when(orderService.createOrder(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

    mockMvc.perform(post("/graphql")
            .header("Authorization", "Bearer write-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"mutation { createOrder(customer: \\\"Ada\\\", amount: 10.50) { customer } }\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors").doesNotExist());
  }

  /** Confirma que la creación REST sin token se rechaza. */
  @Test
  void rejectsOrderCreationWithoutToken() throws Exception {
    mockMvc.perform(post("/api/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"customer\":\"Ada\",\"amount\":10.50}"))
        .andExpect(status().isUnauthorized());
  }

  /** Confirma que el endpoint de salud sigue accesible sin autenticación. */
  @Test
  void keepsHealthEndpointPublic() throws Exception {
    mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  /** Prepara el decodificador para devolver un token de prueba con los roles indicados. */
  private void stubToken(String tokenValue, String... roles) {
    when(jwtDecoder.decode(tokenValue)).thenReturn(org.springframework.security.oauth2.jwt.Jwt
        .withTokenValue(tokenValue)
        .header("alg", "RS256")
        .claim("sub", "test-user")
        .claim("realm_access", Map.of("roles", List.of(roles)))
        .issuedAt(Instant.now())
        .expiresAt(Instant.now().plusSeconds(300))
        .build());
  }
}
