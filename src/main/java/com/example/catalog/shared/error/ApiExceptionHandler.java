package com.example.catalog.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ProblemDetail> api(ApiException e, HttpServletRequest request) {
    return problem(e.status(), e.getMessage(), request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> validation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    var p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
    p.setInstance(URI.create(request.getRequestURI()));
    p.setProperty(
        "errors",
        e.getBindingResult().getFieldErrors().stream()
            .map(f -> new FieldError(f.getField(), f.getDefaultMessage()))
            .toList());
    return ResponseEntity.badRequest().body(p);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ProblemDetail> malformed(Exception e, HttpServletRequest r) {
    return problem(HttpStatus.BAD_REQUEST, "Malformed request body or query parameter", r);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ProblemDetail> integrity(
      DataIntegrityViolationException e, HttpServletRequest r) {
    String state = sqlState(e);
    if ("23505".equals(state)) return problem(HttpStatus.CONFLICT, "SKU already exists", r);
    if ("23503".equals(state)) return problem(HttpStatus.BAD_REQUEST, "Category does not exist", r);
    if (List.of("23514", "23502", "22001", "22003").contains(state))
      return problem(HttpStatus.BAD_REQUEST, "Product violates data constraints", r);
    log.error("Unexpected database constraint failure", e);
    return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected persistence error", r);
  }

  @ExceptionHandler(DataAccessException.class)
  public ResponseEntity<ProblemDetail> database(DataAccessException e, HttpServletRequest r) {
    log.error("Database operation failed", e);
    return problem(HttpStatus.SERVICE_UNAVAILABLE, "Catalog storage is temporarily unavailable", r);
  }

  private static String sqlState(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause())
      if (t instanceof SQLException s) return s.getSQLState() == null ? "" : s.getSQLState();
    return "";
  }

  private ResponseEntity<ProblemDetail> problem(
      HttpStatus status, String detail, HttpServletRequest r) {
    var p = ProblemDetail.forStatusAndDetail(status, detail);
    p.setInstance(URI.create(r.getRequestURI()));
    return ResponseEntity.status(status).body(p);
  }

  public record FieldError(String field, String message) {}
}
