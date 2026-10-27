package com.example.catalog.product.api;

import com.example.catalog.product.application.*;
import com.example.catalog.product.domain.Product;
import com.example.catalog.shared.http.ProductEtags;
import com.example.catalog.shared.pagination.CursorPage;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductWriteRequest request) {
    Product p = service.create(request);
    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(p.id())
            .toUri();
    return ResponseEntity.created(location)
        .eTag(ProductEtags.format(p.id(), p.version()))
        .body(ProductResponse.from(p));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ProductResponse> get(@PathVariable long id) {
    return response(service.get(id));
  }

  @GetMapping
  public CursorPage<ProductSummary> list(
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor) {
    return service.list(new ProductQuery(categoryId, minPrice, maxPrice, limit, cursor));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ProductResponse> update(
      @PathVariable long id,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      @Valid @RequestBody ProductWriteRequest request) {
    return response(service.update(id, ProductEtags.expectedVersion(id, ifMatch), request));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable long id, @RequestHeader(value = "If-Match", required = false) String ifMatch) {
    service.delete(id, ProductEtags.expectedVersion(id, ifMatch));
    return ResponseEntity.noContent().build();
  }

  private ResponseEntity<ProductResponse> response(Product p) {
    return ResponseEntity.ok()
        .eTag(ProductEtags.format(p.id(), p.version()))
        .body(ProductResponse.from(p));
  }
}
