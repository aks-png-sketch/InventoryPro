package com.inventorypro;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.*;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean CorsConfigurationSource corsConfigurationSource(@Value("${FRONTEND_ORIGINS:}") String origins) {
        var cors=new CorsConfiguration();
        List<String> allowed=Arrays.stream(origins.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
        for(String origin:allowed) {
            var uri=java.net.URI.create(origin);
            if(!Set.of("http","https").contains(Objects.toString(uri.getScheme(),"")) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || !(uri.getPath()==null || uri.getPath().isEmpty()))
                throw new IllegalArgumentException("FRONTEND_ORIGINS must contain exact HTTP(S) origins without paths or wildcards");
        }
        cors.setAllowedOrigins(allowed);cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type","X-CSRF-TOKEN"));
        cors.setExposedHeaders(List.of("Content-Disposition"));
        var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/api/**",cors);return source;
    }
    @Bean UserDetailsService users(JdbcTemplate jdbc) {
        return username -> jdbc.query("SELECT username,password_hash,role FROM users WHERE username=?",
            (rs,i) -> User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).build(),username)
            .stream().findFirst().orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
    @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http
            .cors(org.springframework.security.config.Customizer.withDefaults())
            .csrf(c -> c.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .authorizeHttpRequests(a -> a.requestMatchers("/","/index.html","/assets/**","/favicon.ico","/api/auth/csrf","/api/health","/error").permitAll().anyRequest().authenticated())
            .formLogin(f -> f.loginProcessingUrl("/api/auth/login")
                .successHandler((req,res,auth) -> { res.setStatus(200); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Signed in\"}"); })
                .failureHandler((req,res,e) -> { res.setStatus(e instanceof org.springframework.security.authentication.InternalAuthenticationServiceException ? 503 : 401); res.setContentType("application/json"); res.getWriter().write("{\"message\":\"Sign-in failed. Check credentials and database availability.\"}"); }))
            .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((req,res,a) -> res.setStatus(204)))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> jsonError(res,401,"Sign in to continue"))
                .accessDeniedHandler((req,res,ex) -> jsonError(res,403,"Access denied or CSRF token expired. Refresh and try again.")))
            .build();
    }
    private static void jsonError(HttpServletResponse res,int status,String message) throws java.io.IOException {
        res.setStatus(status); res.setContentType("application/json"); res.getWriter().write("{\"message\":\""+message+"\"}");
    }
}
