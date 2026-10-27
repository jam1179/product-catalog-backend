package com.example.catalog;

import static org.assertj.core.api.Assertions.*;

import com.example.catalog.shared.error.ApiException;
import com.example.catalog.shared.http.ProductEtags;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProductEtagsTest {
  @Test
  void roundTrip() {
    assertThat(ProductEtags.expectedVersion(7, ProductEtags.format(7, 3))).isEqualTo(3);
  }

  @Test
  void requiresHeader() {
    assertThatThrownBy(() -> ProductEtags.expectedVersion(7, null))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED));
  }

  @Test
  void rejectsWeakWildcardMultipleAndOverflowTags() {
    for (String tag :
        new String[] {
          "W/\"product-7-v3\"",
          "*",
          "\"product-7-v3\", \"product-7-v4\"",
          "\"product-7-v999999999999999999999999\""
        })
      assertThatThrownBy(() -> ProductEtags.expectedVersion(7, tag))
          .isInstanceOf(ApiException.class);
  }

  @Test
  void differentProductFailsPrecondition() {
    assertThatThrownBy(() -> ProductEtags.expectedVersion(7, ProductEtags.format(8, 3)))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.PRECONDITION_FAILED));
  }
}
