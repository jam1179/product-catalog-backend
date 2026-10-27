package com.example.catalog.shared.pagination;

import com.example.catalog.product.api.ProductSummary;
import com.example.catalog.product.application.ProductQuery;
import com.example.catalog.shared.error.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Stateless cursors: shared signing key required across every application instance. */
@Component
public class CursorCodec {
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final ObjectMapper mapper;
  private final byte[] key;

  public CursorCodec(ObjectMapper mapper, @Value("${catalog.cursor-secret}") String secret) {
    this.mapper = mapper;
    key = secret.getBytes(StandardCharsets.UTF_8);
    if (key.length < 32)
      throw new IllegalArgumentException("CURSOR_SECRET must contain at least 32 UTF-8 bytes");
  }

  public String encode(ProductSummary last, ProductQuery query) {
    try {
      byte[] bytes =
          mapper.writeValueAsBytes(
              new Payload(
                  1, last.price().toPlainString(), last.id(), fingerprint(query), "price_asc"));
      String payload = ENCODER.encodeToString(bytes);
      return payload + "." + ENCODER.encodeToString(sign(payload));
    } catch (Exception e) {
      throw new IllegalStateException("Cannot encode cursor", e);
    }
  }

  public CursorPosition decodeAndValidate(String token, ProductQuery query) {
    if (token == null) return null;
    try {
      if (token.isBlank() || token.length() > 2048) throw new IllegalArgumentException();
      String[] parts = token.split("\\.", -1);
      if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1])))
        throw new IllegalArgumentException();
      Payload p = mapper.readValue(DECODER.decode(parts[0]), Payload.class);
      BigDecimal price = new BigDecimal(p.lastPrice());
      if (p.version() != 1
          || p.lastId() <= 0
          || !"price_asc".equals(p.sort())
          || !fingerprint(query).equals(p.filterHash())
          || price.signum() < 0
          || price.scale() > 2
          || price.compareTo(new BigDecimal("9999999999.99")) > 0)
        throw new IllegalArgumentException();
      return new CursorPosition(price, p.lastId());
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "Invalid cursor or cursor does not match filters");
    }
  }

  private byte[] sign(String payload) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII));
  }

  private String fingerprint(ProductQuery q) throws Exception {
    String canonical =
        q.categoryId() + "|" + amount(q.minPrice()) + "|" + amount(q.maxPrice()) + "|price_asc";
    return ENCODER.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
  }

  private String amount(BigDecimal value) {
    return value == null ? "-" : value.stripTrailingZeros().toPlainString();
  }

  public record Payload(
      int version, String lastPrice, long lastId, String filterHash, String sort) {}
}
