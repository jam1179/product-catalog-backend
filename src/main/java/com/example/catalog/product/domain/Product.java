package com.example.catalog.product.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record Product(
    long id,
    String sku,
    String name,
    String description,
    long categoryId,
    BigDecimal price,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
