package com.example.catalog.product.persistence;

import com.example.catalog.product.api.ProductSummary;
import com.example.catalog.product.api.ProductWriteRequest;
import com.example.catalog.product.application.ProductQuery;
import com.example.catalog.product.domain.Product;
import com.example.catalog.shared.pagination.CursorPosition;
import java.util.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProductRepository {
  private final JdbcClient jdbc;
  private static final RowMapper<Product> PRODUCT =
      (rs, n) ->
          new Product(
              rs.getLong("id"),
              rs.getString("sku"),
              rs.getString("name"),
              rs.getString("description"),
              rs.getLong("category_id"),
              rs.getBigDecimal("price"),
              rs.getLong("version"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  public ProductRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<Product> findById(long id) {
    return jdbc.sql("SELECT * FROM product WHERE id = :id")
        .param("id", id)
        .query(PRODUCT)
        .optional();
  }

  public Product create(ProductWriteRequest request) {
    return jdbc.sql(
            """
            INSERT INTO product(sku, name, description, category_id, price)
            VALUES (:sku, :name, :description, :categoryId, :price) RETURNING *
            """)
        .params(parameters(request))
        .query(PRODUCT)
        .single();
  }

  public Optional<Product> update(long id, long version, ProductWriteRequest request) {
    Map<String, Object> params = parameters(request);
    params.put("id", id);
    params.put("version", version);
    return jdbc.sql(
            """
            UPDATE product SET sku = :sku, name = :name, description = :description,
                category_id = :categoryId, price = :price, version = version + 1,
                updated_at = clock_timestamp()
            WHERE id = :id AND version = :version RETURNING *
            """)
        .params(params)
        .query(PRODUCT)
        .optional();
  }

  public boolean delete(long id, long version) {
    return jdbc.sql("DELETE FROM product WHERE id = :id AND version = :version")
            .param("id", id)
            .param("version", version)
            .update()
        == 1;
  }

  public List<ProductSummary> find(ProductQuery q, CursorPosition cursor, int fetchSize) {
    var sql = new StringBuilder("SELECT id, sku, name, category_id, price FROM product WHERE 1=1");
    Map<String, Object> params = new HashMap<>();
    if (q.categoryId() != null) {
      sql.append(" AND category_id = :categoryId");
      params.put("categoryId", q.categoryId());
    }
    if (q.minPrice() != null) {
      sql.append(" AND price >= :minPrice");
      params.put("minPrice", q.minPrice());
    }
    if (q.maxPrice() != null) {
      sql.append(" AND price <= :maxPrice");
      params.put("maxPrice", q.maxPrice());
    }
    if (cursor != null) {
      sql.append(" AND (price, id) > (:lastPrice, :lastId)");
      params.put("lastPrice", cursor.price());
      params.put("lastId", cursor.id());
    }
    sql.append(" ORDER BY price ASC, id ASC LIMIT :fetchSize");
    params.put("fetchSize", fetchSize);
    return jdbc.sql(sql.toString())
        .params(params)
        .query(
            (rs, n) ->
                new ProductSummary(
                    rs.getLong("id"),
                    rs.getString("sku"),
                    rs.getString("name"),
                    rs.getLong("category_id"),
                    rs.getBigDecimal("price"),
                    "EUR"))
        .list();
  }

  private Map<String, Object> parameters(ProductWriteRequest r) {
    // HashMap permits nullable description; Map.of does not.
    Map<String, Object> p = new HashMap<>();
    p.put("sku", r.sku().toUpperCase(Locale.ROOT));
    p.put("name", r.name().trim());
    p.put("description", r.description());
    p.put("categoryId", r.categoryId());
    p.put("price", r.price());
    return p;
  }
}
