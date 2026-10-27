package com.example.catalog.shared.pagination;

import java.util.List;

public record CursorPage<T>(List<T> items, boolean hasNext, String nextCursor) {}
