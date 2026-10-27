package com.example.catalog.product.api;

import java.math.BigDecimal;

public record ProductSummary(
    long id, String sku, String name, long categoryId, BigDecimal price, String currency) {}
