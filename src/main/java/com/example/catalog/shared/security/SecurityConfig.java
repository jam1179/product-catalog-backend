package com.example.catalog.shared.security;

import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
  @Bean
  @Profile("local")
  SecurityFilterChain local(HttpSecurity http) throws Exception {
    return base(http).authorizeHttpRequests(a -> a.anyRequest().permitAll()).build();
  }

  @Bean
  @Profile("!local")
  SecurityFilterChain secured(HttpSecurity http) throws Exception {
    return base(http)
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products",
                        "/api/v1/products/*",
                        "/api/v1/categories")
                    .permitAll()
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .hasAuthority("SCOPE_catalog.ops")
                    .requestMatchers(HttpMethod.POST, "/api/v1/products")
                    .hasAuthority("SCOPE_catalog.write")
                    .requestMatchers(HttpMethod.PUT, "/api/v1/products/*")
                    .hasAuthority("SCOPE_catalog.write")
                    .requestMatchers(HttpMethod.DELETE, "/api/v1/products/*")
                    .hasAuthority("SCOPE_catalog.write")
                    .anyRequest()
                    .denyAll())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()))
        .build();
  }

  private HttpSecurity base(HttpSecurity http) throws Exception {
    // Bearer-token API: no cookie-based authentication or server session.
    return http.csrf(c -> c.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
  }
}
