package com.example.catalog.product.api;

import com.example.catalog.product.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
    long id,
    String sku,
    String name,
    String description,
    long categoryId,
    BigDecimal price,
    String currency,
    long version,
    Instant createdAt,
    Instant updatedAt) {
  public static ProductResponse from(Product p) {
    return new ProductResponse(
        p.id(),
        p.sku(),
        p.name(),
        p.description(),
        p.categoryId(),
        p.price(),
        "EUR",
        p.version(),
        p.createdAt(),
        p.updatedAt());
  }
}
