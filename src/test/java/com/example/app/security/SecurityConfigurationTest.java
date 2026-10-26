package com.example.app.security;

import com.example.app.repository.OrderRepository;
import com.example.app.domain.Customer;
import com.example.app.domain.Order;
import com.example.app.service.CustomerService;
import com.example.app.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
  private CustomerService customerService;

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

  /** Confirma que ORDER_READ puede solicitar el promedio mediante POST. */
  @Test
  void allowsAverageRequestForReadRole() throws Exception {
    stubToken("read-token", "ORDER_READ");
    when(orderService.averageAmountCreatedBetween(
        Instant.parse("2026-10-01T00:00:00Z"), Instant.parse("2026-11-01T00:00:00Z")))
        .thenReturn(new BigDecimal("17.25"));

    mockMvc.perform(post("/api/orders/average-amount")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-11-01T00:00:00Z\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.averageAmount").value(17.25));
  }

  /** Confirma que ORDER_WRITE sin ORDER_READ no puede consultar el promedio. */
  @Test
  void rejectsAverageRequestForWriteOnlyRole() throws Exception {
    stubToken("write-token", "ORDER_WRITE");

    mockMvc.perform(post("/api/orders/average-amount")
            .header("Authorization", "Bearer write-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"from\":\"2026-10-01T00:00:00Z\",\"to\":\"2026-11-01T00:00:00Z\"}"))
        .andExpect(status().isForbidden());
  }

  /** Confirma que el endpoint rechaza un intervalo vacío o invertido. */
  @Test
  void rejectsInvalidAverageRange() throws Exception {
    stubToken("read-token", "ORDER_READ");

    mockMvc.perform(post("/api/orders/average-amount")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"from\":\"2026-11-01T00:00:00Z\",\"to\":\"2026-10-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());
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

  /** Confirma que ORDER_READ permite consultar clientes por GraphQL. */
  @Test
  void allowsGraphQlCustomerQueryForReadRole() throws Exception {
    stubToken("read-token", "ORDER_READ");
    Customer customer = new Customer("Ada");
    ReflectionTestUtils.setField(customer, "id", 21L);
    Order order = new Order();
    order.setCustomerRecord(customer);
    order.setId(51);
    order.setAmount(new BigDecimal("10.50"));
    when(customerService.findAll()).thenReturn(List.of(customer));
    when(orderService.findOrdersByCustomerIds(Set.of(21L))).thenReturn(Map.of(21L, List.of(order)));

    mockMvc.perform(post("/graphql")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"{ customers { id name orders { id amount } } }\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors").doesNotExist())
        .andExpect(jsonPath("$.data.customers[0].orders[0].amount").value(10.5));
  }

  /** Confirma que ORDER_READ no permite crear clientes por GraphQL. */
  @Test
  void rejectsGraphQlCustomerCreationForReadOnlyRole() throws Exception {
    stubToken("read-token", "ORDER_READ");

    mockMvc.perform(post("/graphql")
            .header("Authorization", "Bearer read-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"mutation { createCustomer(name: \\\"Ada\\\") { id name } }\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.errors").exists());
  }

  /** Confirma que ORDER_WRITE permite crear clientes por GraphQL. */
  @Test
  void allowsGraphQlCustomerCreationForWriteRole() throws Exception {
    stubToken("write-token", "ORDER_WRITE");
    when(customerService.create("Ada")).thenReturn(new com.example.app.domain.Customer("Ada"));

    mockMvc.perform(post("/graphql")
            .header("Authorization", "Bearer write-token")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"query\":\"mutation { createCustomer(name: \\\"Ada\\\") { name } }\"}"))
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
