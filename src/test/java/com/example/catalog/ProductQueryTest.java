package com.example.catalog;

import static org.assertj.core.api.Assertions.*;

import com.example.catalog.product.application.ProductQuery;
import com.example.catalog.shared.error.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProductQueryTest {
  @Test
  void validBoundsAndLimit() {
    assertThatCode(
            () ->
                new ProductQuery(1L, BigDecimal.ZERO, new BigDecimal("9999999999.99"), 100, null)
                    .validate())
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsInvalidBoundsAndLimits() {
    for (ProductQuery q :
        new ProductQuery[] {
          new ProductQuery(0L, null, null, 20, null),
          new ProductQuery(null, null, null, 0, null),
          new ProductQuery(null, null, null, 101, null),
          new ProductQuery(null, new BigDecimal("-1"), null, 20, null),
          new ProductQuery(null, new BigDecimal("0.001"), null, 20, null),
          new ProductQuery(null, null, new BigDecimal("10000000000"), 20, null),
          new ProductQuery(null, new BigDecimal("20"), new BigDecimal("10"), 20, null)
        }) assertThatThrownBy(q::validate).isInstanceOf(ApiException.class);
  }
}
