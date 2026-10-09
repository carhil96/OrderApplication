package com.example.app.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtDecoderValidationTest {

  private static final String ISSUER = "https://issuer.example.test";
  private static final String KEY_ID = "test-rsa-key";
  private static KeyPair signingKey;
  private static HttpServer jwksServer;
  private static JwtDecoder decoder;

  /** Genera una clave de prueba y prepara el servidor JWKS y el decodificador. */
  @BeforeAll
  static void startJwksServerAndDecoder() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    signingKey = generator.generateKeyPair();

    String jwks = "{\"keys\":[" + new RSAKey.Builder((RSAPublicKey) signingKey.getPublic())
        .keyID(KEY_ID)
        .algorithm(JWSAlgorithm.RS256)
        .build()
        .toPublicJWK()
        .toJSONString() + "]}";

    jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    jwksServer.createContext("/jwks", exchange -> {
      byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    jwksServer.start();

    String jwkSetUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    decoder = new SecurityConfiguration().jwtDecoder(ISSUER, jwkSetUri);
  }

  /** Detiene el servidor JWKS local al terminar las pruebas. */
  @AfterAll
  static void stopJwksServer() {
    if (jwksServer != null) {
      jwksServer.stop(0);
    }
  }

  /** Confirma que se acepta un JWT firmado con la clave e issuer esperados. */
  @Test
  void acceptsValidSignedJwtFromConfiguredIssuer() throws Exception {
    var jwt = decoder.decode(token(ISSUER, Instant.now().plusSeconds(300), signingKey));

    assertEquals("test-user", jwt.getSubject());
  }

  /** Confirma que se rechaza un JWT firmado con una clave no confiable. */
  @Test
  void rejectsJwtSignedByUntrustedKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair untrustedKey = generator.generateKeyPair();

    assertThrows(JwtException.class,
        () -> decoder.decode(token(ISSUER, Instant.now().plusSeconds(300), untrustedKey)));
  }

  /** Confirma que se rechaza un JWT emitido por otro issuer. */
  @Test
  void rejectsJwtWithUnexpectedIssuer() throws Exception {
    assertThrows(JwtException.class,
        () -> decoder.decode(token("https://other-issuer.example.test", Instant.now().plusSeconds(300), signingKey)));
  }

  /** Confirma que se rechaza un JWT cuya fecha de expiración ya pasó. */
  @Test
  void rejectsExpiredJwt() throws Exception {
    assertThrows(JwtException.class,
        () -> decoder.decode(token(ISSUER, Instant.now().minusSeconds(30), signingKey)));
  }

  /** Firma y serializa un JWT de prueba con issuer y expiración configurables. */
  private static String token(String issuer, Instant expiresAt, KeyPair keyPair) throws Exception {
    Instant now = Instant.now();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject("test-user")
        .issueTime(Date.from(now.minusSeconds(1)))
        .expirationTime(Date.from(expiresAt))
        .build();

    SignedJWT signedJwt = new SignedJWT(
        new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(KEY_ID)
            .type(JOSEObjectType.JWT)
            .build(),
        claims);
    signedJwt.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));
    return signedJwt.serialize();
  }
}
