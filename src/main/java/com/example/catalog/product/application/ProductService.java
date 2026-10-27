package com.example.catalog.product.application;

import com.example.catalog.product.api.*;
import com.example.catalog.product.domain.Product;
import com.example.catalog.product.persistence.ProductRepository;
import com.example.catalog.shared.error.ApiException;
import com.example.catalog.shared.pagination.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {
  private final ProductRepository repository;
  private final CursorCodec cursorCodec;

  public ProductService(ProductRepository repository, CursorCodec cursorCodec) {
    this.repository = repository;
    this.cursorCodec = cursorCodec;
  }

  public Product get(long id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found"));
  }

  @Transactional
  public Product create(ProductWriteRequest request) {
    return repository.create(request);
  }

  @Transactional
  public Product update(long id, long version, ProductWriteRequest request) {
    return repository.update(id, version, request).orElseThrow(ProductService::preconditionFailed);
  }

  @Transactional
  public void delete(long id, long version) {
    if (!repository.delete(id, version)) throw preconditionFailed();
  }

  public CursorPage<ProductSummary> list(ProductQuery query) {
    query.validate();
    CursorPosition cursor = cursorCodec.decodeAndValidate(query.cursor(), query);
    List<ProductSummary> rows = repository.find(query, cursor, query.limit() + 1);
    boolean hasNext = rows.size() > query.limit();
    List<ProductSummary> items = List.copyOf(rows.subList(0, Math.min(rows.size(), query.limit())));
    String next = hasNext ? cursorCodec.encode(items.get(items.size() - 1), query) : null;
    return new CursorPage<>(items, hasNext, next);
  }

  private static ApiException preconditionFailed() {
    return new ApiException(
        HttpStatus.PRECONDITION_FAILED,
        "Product was changed or no longer exists; retrieve it before retrying");
  }
}
