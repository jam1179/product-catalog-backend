package com.example.catalog.product.application;

import com.example.catalog.shared.error.ApiException;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;

public record ProductQuery(
    Long categoryId, BigDecimal minPrice, BigDecimal maxPrice, int limit, String cursor) {
  private static final BigDecimal MAX_PRICE = new BigDecimal("9999999999.99");

  public void validate() {
    if (categoryId != null && categoryId <= 0) bad("categoryId must be positive");
    if (limit < 1 || limit > 100) bad("limit must be between 1 and 100");
    validatePrice(minPrice);
    validatePrice(maxPrice);
    if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0)
      bad("minPrice must not exceed maxPrice");
  }

  private static void validatePrice(BigDecimal p) {
    if (p != null
        && (p.signum() < 0 || p.compareTo(MAX_PRICE) > 0 || p.stripTrailingZeros().scale() > 2))
      bad(
          "price bounds must be nonnegative, within NUMERIC(12,2), with at most two decimal"
              + " places");
  }

  private static void bad(String message) {
    throw new ApiException(HttpStatus.BAD_REQUEST, message);
  }
}
