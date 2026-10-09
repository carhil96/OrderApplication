package com.example.app.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {

  /** Define las rutas públicas y protegidas y activa la validación de JWT. */
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(authorize -> authorize
            .requestMatchers("/actuator/health").permitAll()
            .requestMatchers(HttpMethod.GET, "/api/orders/**").hasAuthority("ORDER_READ")
            .requestMatchers(HttpMethod.POST, "/api/orders/**").hasAuthority("ORDER_WRITE")
            .requestMatchers("/api/orders/**", "/graphql").authenticated()
            .anyRequest().denyAll())
        .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt ->
            jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
        .build();
  }

  /** Convierte los roles del JWT en permisos que Spring Security pueda comprobar. */
  private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      Collection<GrantedAuthority> authorities = new ArrayList<>(scopesConverter.convert(jwt));
      Object realmAccessClaim = jwt.getClaim("realm_access");
      if (realmAccessClaim instanceof Map<?, ?> realmAccess
          && realmAccess.get("roles") instanceof Collection<?> roles) {
        roles.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .map(SimpleGrantedAuthority::new)
            .forEach(authorities::add);
      }
      return authorities;
    });
    return converter;
  }

  /** Crea el decodificador JWT usando el emisor configurado y su conjunto de claves. */
  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  JwtDecoder jwtDecoder(
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri,
      @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri) {
    if (!StringUtils.hasText(issuerUri)) {
      throw new IllegalStateException(
          "spring.security.oauth2.resourceserver.jwt.issuer-uri must be configured");
    }
    NimbusJwtDecoder decoder;
    if (StringUtils.hasText(jwkSetUri)) {
      decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
          .jwsAlgorithm(SignatureAlgorithm.RS256)
          .build();
    } else {
      decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
    }
    OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(issuerUri);
    decoder.setJwtValidator(validator);
    return decoder;
  }
}
