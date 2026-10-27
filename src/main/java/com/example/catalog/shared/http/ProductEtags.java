package com.example.catalog.shared.http;

import com.example.catalog.shared.error.ApiException;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

public final class ProductEtags {
  private static final Pattern TAG = Pattern.compile("\"product-([1-9][0-9]*)-v([0-9]+)\"");

  private ProductEtags() {}

  public static String format(long id, long version) {
    return "\"product-" + id + "-v" + version + "\"";
  }

  public static long expectedVersion(long id, String header) {
    if (header == null || header.isBlank())
      throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "If-Match is required");
    // This API deliberately accepts one concrete strong product ETag only.
    var matcher = TAG.matcher(header);
    if (!matcher.matches())
      throw new ApiException(HttpStatus.BAD_REQUEST, "If-Match must be one strong product ETag");
    try {
      if (Long.parseLong(matcher.group(1)) != id)
        throw new ApiException(HttpStatus.PRECONDITION_FAILED, "ETag belongs to another product");
      return Long.parseLong(matcher.group(2));
    } catch (NumberFormatException e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "ETag value is out of range");
    }
  }
}
