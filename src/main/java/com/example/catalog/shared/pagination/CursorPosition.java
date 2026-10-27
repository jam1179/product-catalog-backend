package com.example.catalog.shared.pagination;

import java.math.BigDecimal;

public record CursorPosition(BigDecimal price, long id) {}
