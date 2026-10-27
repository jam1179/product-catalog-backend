package com.example.catalog.product.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductWriteRequest(
    @NotBlank
        @Size(max = 64)
        @Pattern(
            regexp = "[A-Za-z0-9][A-Za-z0-9._-]*",
            message = "must contain only letters, digits, dots, underscores or hyphens")
        String sku,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 20000) String description,
    @NotNull @Positive Long categoryId,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal price) {}
