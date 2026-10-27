package com.example.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.catalog.product.api.ProductController;
import com.example.catalog.product.application.ProductService;
import com.example.catalog.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {
  @Autowired MockMvc mvc;
  @MockitoBean ProductService service;
  @MockitoBean JwtDecoder decoder;

  @Test
  void readsArePublic() throws Exception {
    mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
  }

  @Test
  void anonymousWritesRequireAuthentication() throws Exception {
    mvc.perform(post("/api/v1/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void writesRequireScope() throws Exception {
    mvc.perform(post("/api/v1/products").with(jwt())).andExpect(status().isForbidden());
  }

  @Test
  void authorizedWritesReachValidation() throws Exception {
    mvc.perform(
            post("/api/v1/products")
                .with(jwt().authorities(() -> "SCOPE_catalog.write"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
