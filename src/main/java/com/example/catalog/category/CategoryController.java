package com.example.catalog.category;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
  private final JdbcClient jdbc;

  public CategoryController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  public List<Category> list() {
    return jdbc.sql("SELECT id, name FROM category ORDER BY id").query(Category.class).list();
  }

  public record Category(long id, String name) {}
}
