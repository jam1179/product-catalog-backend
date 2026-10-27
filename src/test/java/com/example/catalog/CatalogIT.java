package com.example.catalog;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.catalog.product.api.ProductWriteRequest;
import com.example.catalog.product.application.ProductService;
import com.example.catalog.product.persistence.ProductRepository;
import com.example.catalog.shared.error.ApiException;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Real PostgreSQL only. Uses Docker by default; external disposable DB is an explicit alternative.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CatalogIT {
  static PostgreSQLContainer<?> postgres;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    String external = System.getProperty("catalog.it.db-url");
    if (external == null) {
      postgres = new PostgreSQLContainer<>("postgres:17.6");
      postgres.start();
      r.add("spring.datasource.url", postgres::getJdbcUrl);
      r.add("spring.datasource.username", postgres::getUsername);
      r.add("spring.datasource.password", postgres::getPassword);
    } else {
      r.add("spring.datasource.url", () -> external);
      r.add(
          "spring.datasource.username", () -> System.getProperty("catalog.it.db-user", "catalog"));
      r.add(
          "spring.datasource.password",
          () -> System.getProperty("catalog.it.db-password", "catalog"));
    }
    r.add("catalog.cursor-secret", () -> "integration-test-key-at-least-32-bytes");
  }

  @AfterAll
  static void stopDatabase() {
    if (postgres != null) postgres.stop();
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired ProductService service;
  @Autowired ProductRepository repository;

  @BeforeEach
  void clean() {
    jdbc.execute("TRUNCATE product RESTART IDENTITY");
  }

  private long category() {
    return jdbc.queryForObject("SELECT min(id) FROM category", Long.class);
  }

  private ProductWriteRequest request(String sku, String price) {
    return new ProductWriteRequest(sku, "Product", null, category(), new BigDecimal(price));
  }

  private MvcResult create(String sku, String price) throws Exception {
    return mvc.perform(
            post("/api/v1/products")
                .contentType("application/json")
                .content(mapper.writeValueAsBytes(request(sku, price))))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(header().exists("ETag"))
        .andReturn();
  }

  private JsonNode body(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  @Test
  void fullCrudAndPreconditions() throws Exception {
    var created = create("phone-001", "499.99");
    long id = body(created).get("id").asLong();
    String tag = created.getResponse().getHeader("ETag");
    mvc.perform(get("/api/v1/products/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sku").value("PHONE-001"));
    byte[] payload = mapper.writeValueAsBytes(request("PHONE-001", "459.99"));
    mvc.perform(put("/api/v1/products/{id}", id).contentType("application/json").content(payload))
        .andExpect(status().isPreconditionRequired());
    var updated =
        mvc.perform(
                put("/api/v1/products/{id}", id)
                    .header("If-Match", tag)
                    .contentType("application/json")
                    .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1))
            .andReturn();
    mvc.perform(
            put("/api/v1/products/{id}", id)
                .header("If-Match", tag)
                .contentType("application/json")
                .content(payload))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(delete("/api/v1/products/{id}", id).header("If-Match", tag))
        .andExpect(status().isPreconditionFailed());
    mvc.perform(
            delete("/api/v1/products/{id}", id)
                .header("If-Match", updated.getResponse().getHeader("ETag")))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/v1/products/{id}", id)).andExpect(status().isNotFound());
  }

  @Test
  void rejectsDuplicateSkuAndInvalidCategoryAndPrice() throws Exception {
    create("DUP", "10");
    mvc.perform(
            post("/api/v1/products")
                .contentType("application/json")
                .content(mapper.writeValueAsBytes(request("dup", "20"))))
        .andExpect(status().isConflict())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    var invalid = new ProductWriteRequest("OTHER", "Product", null, Long.MAX_VALUE, BigDecimal.ONE);
    mvc.perform(
            post("/api/v1/products")
                .contentType("application/json")
                .content(mapper.writeValueAsBytes(invalid)))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/products")
                .contentType("application/json")
                .content(mapper.writeValueAsBytes(request("NEG", "-1"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/v1/products")
                .contentType("application/json")
                .content(mapper.writeValueAsBytes(request("FRACTION", "1.001"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void keysetPaginationHandlesEqualPricesAndInclusiveBounds() throws Exception {
    create("LOW", "9");
    create("A", "10");
    create("B", "10");
    create("C", "10");
    create("D", "20");
    create("HIGH", "21");
    Set<Long> ids = new LinkedHashSet<>();
    String cursor = null;
    int pages = 0;
    do {
      var req =
          get("/api/v1/products")
              .param("categoryId", Long.toString(category()))
              .param("minPrice", "10")
              .param("maxPrice", "20")
              .param("limit", "2");
      if (cursor != null) req.param("cursor", cursor);
      JsonNode page = body(mvc.perform(req).andExpect(status().isOk()).andReturn());
      for (JsonNode item : page.get("items")) assertThat(ids.add(item.get("id").asLong())).isTrue();
      cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
      assertThat(page.get("hasNext").asBoolean()).isEqualTo(cursor != null);
      assertThat(++pages).isLessThanOrEqualTo(3);
    } while (cursor != null);
    assertThat(ids).hasSize(4);
    assertThat(pages).isEqualTo(2);
  }

  @Test
  void globalAndCategoryOnlyQueriesAndCursorRejection() throws Exception {
    create("A", "10");
    create("B", "10");
    var page =
        body(
            mvc.perform(get("/api/v1/products").param("limit", "1"))
                .andExpect(status().isOk())
                .andReturn());
    String cursor = page.get("nextCursor").asText();
    mvc.perform(
            get("/api/v1/products")
                .param("limit", "1")
                .param("cursor", cursor)
                .param("categoryId", Long.toString(category())))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/products").param("cursor", "broken"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/products").param("categoryId", Long.toString(category())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get("/api/v1/products").param("minPrice", "100"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get("/api/v1/products").param("minPrice", "20").param("maxPrice", "10"))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/products").param("limit", "101")).andExpect(status().isBadRequest());
  }

  @Test
  void concurrentUpdatesCannotSilentlyOverwrite() throws Exception {
    var p = service.create(request("RACE", "10"));
    List<Boolean> outcomes =
        race(
            i -> {
              try {
                service.update(p.id(), p.version(), request("RACE", Integer.toString(20 + i)));
                return true;
              } catch (ApiException e) {
                assertThat(e.status().value()).isEqualTo(412);
                return false;
              }
            });
    assertThat(outcomes).containsExactlyInAnyOrder(true, false);
    assertThat(service.get(p.id()).version()).isEqualTo(1);
  }

  @Test
  void concurrentDuplicateCreatesHaveOneWinner() throws Exception {
    var r = request("DUP-RACE", "10");
    List<Boolean> outcomes =
        race(
            i -> {
              try {
                service.create(r);
                return true;
              } catch (DuplicateKeyException e) {
                return false;
              }
            });
    assertThat(outcomes).containsExactlyInAnyOrder(true, false);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM product WHERE sku='DUP-RACE'", Long.class))
        .isEqualTo(1);
  }

  @Test
  void categoriesAndMissingResources() throws Exception {
    mvc.perform(get("/api/v1/categories"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].name").exists());
    mvc.perform(get("/api/v1/products/99999")).andExpect(status().isNotFound());
    mvc.perform(delete("/api/v1/products/99999").header("If-Match", "\"product-99999-v0\""))
        .andExpect(status().isPreconditionFailed());
  }

  private List<Boolean> race(java.util.function.IntFunction<Boolean> action) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
    try {
      List<Future<Boolean>> tasks =
          IntStream.range(0, 2)
              .mapToObj(
                  i ->
                      pool.submit(
                          () -> {
                            ready.countDown();
                            if (!start.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("Start timeout");
                            return action.apply(i);
                          }))
              .toList();
      assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      return List.of(
          tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS));
    } finally {
      start.countDown();
      pool.shutdownNow();
    }
  }
}
