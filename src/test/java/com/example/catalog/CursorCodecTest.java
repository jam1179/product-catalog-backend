package com.example.catalog;

import static org.assertj.core.api.Assertions.*;

import com.example.catalog.product.api.ProductSummary;
import com.example.catalog.product.application.ProductQuery;
import com.example.catalog.shared.error.ApiException;
import com.example.catalog.shared.pagination.CursorCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CursorCodecTest {
  private final CursorCodec codec =
      new CursorCodec(new ObjectMapper(), "test-key-with-at-least-32-bytes-long");
  private final ProductQuery query =
      new ProductQuery(1L, new BigDecimal("10.00"), new BigDecimal("50.00"), 2, null);

  @Test
  void roundTripAndEquivalentDecimalFilters() {
    var token =
        codec.encode(
            new ProductSummary(42, "SKU", "Name", 1, new BigDecimal("12.50"), "EUR"), query);
    var equivalent = new ProductQuery(1L, new BigDecimal("10"), new BigDecimal("50.0"), 100, token);
    var position = codec.decodeAndValidate(token, equivalent);
    assertThat(position.id()).isEqualTo(42);
    assertThat(position.price()).isEqualByComparingTo("12.50");
  }

  @Test
  void rejectsTamperingAndMismatchedFilters() {
    var token =
        codec.encode(
            new ProductSummary(42, "SKU", "Name", 1, new BigDecimal("12.50"), "EUR"), query);
    String changed = (token.charAt(0) == 'A' ? "B" : "A") + token.substring(1);
    assertThatThrownBy(() -> codec.decodeAndValidate(changed, query))
        .isInstanceOf(ApiException.class);
    var other = new ProductQuery(2L, query.minPrice(), query.maxPrice(), 2, token);
    assertThatThrownBy(() -> codec.decodeAndValidate(token, other))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsMalformedBlankAndOversizedTokens() {
    for (String token : new String[] {"", "bad", "x.y.z", "x".repeat(2049)})
      assertThatThrownBy(() -> codec.decodeAndValidate(token, query))
          .isInstanceOf(ApiException.class);
    assertThat(codec.decodeAndValidate(null, query)).isNull();
  }

  @Test
  void requiresStrongSigningSecret() {
    assertThatThrownBy(() -> new CursorCodec(new ObjectMapper(), "short"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
