package com.inventorypro;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.*;
import org.springframework.core.env.Environment;
import java.time.LocalDate;
import java.util.*;
import static com.inventorypro.Models.*;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {
    private final InventoryService service; private final JdbcTemplate jdbc; private final PasswordEncoder encoder; private final Environment env;
    public ApiController(InventoryService service,JdbcTemplate jdbc,PasswordEncoder encoder,Environment env) { this.service=service;this.jdbc=jdbc;this.encoder=encoder;this.env=env; }
    @GetMapping("/health") public Map<String,Object> health() { jdbc.queryForObject("SELECT 1 FROM dual",Integer.class); return Map.of("status","UP","database",env.matchesProfiles("demo") ? "H2 demo" : "Oracle"); }
    @GetMapping("/auth/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/auth/me") public Map<String,Object> me(Authentication auth) { return Map.of("username",auth.getName(),"role",auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_","")); }
    @PostMapping("/users") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public Map<String,String> user(@Valid @RequestBody UserInput input) {
        if(input.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be at most 72 UTF-8 bytes");
        jdbc.update("INSERT INTO users(username,password_hash,role) VALUES(?,?,?)",input.username(),encoder.encode(input.password()),input.role());
        return Map.of("username",input.username(),"role",input.role());
    }
    @GetMapping("/products") public Map<String,Object> products(@RequestParam(defaultValue="") @Size(max=150) String search,@RequestParam(required=false) Long category,
        @RequestParam(defaultValue="all") String status,@RequestParam(defaultValue="name") String sort,@RequestParam(defaultValue="asc") String direction,
        @RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.products(search,category,status,sort,direction,page,size); }
    @GetMapping("/products/{id}") public Map<String,Object> product(@PathVariable long id) { return service.product(id); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> create(@Valid @RequestBody ProductInput p,Authentication a) { return service.createProduct(p,a.getName()); }
    @PutMapping("/products/{id}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody ProductInput p) { return service.updateProduct(id,p); }
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void delete(@PathVariable long id) { service.deleteProduct(id); }
    @GetMapping("/categories") public List<Map<String,Object>> categories() { return service.categories(); }
    @PostMapping("/categories") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> category(@Valid @RequestBody CategoryInput c) { return service.saveCategory(null,c); }
    @PutMapping("/categories/{id}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> category(@PathVariable long id,@Valid @RequestBody CategoryInput c) { return service.saveCategory(id,c); }
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteCategory(@PathVariable long id) { service.deleteCategory(id); }
    @GetMapping("/suppliers") public List<Map<String,Object>> suppliers() { return service.suppliers(); }
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> supplier(@Valid @RequestBody SupplierInput s) { return service.saveSupplier(null,s); }
    @PutMapping("/suppliers/{id}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> supplier(@PathVariable long id,@Valid @RequestBody SupplierInput s) { return service.saveSupplier(id,s); }
    @DeleteMapping("/suppliers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')") public void deleteSupplier(@PathVariable long id) { service.deleteSupplier(id); }
    @PostMapping("/movements") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> move(@Valid @RequestBody MovementInput m,Authentication a) { return service.move(m,a.getName()); }
    @GetMapping("/movements") public Map<String,Object> movements(@RequestParam LocalDate from,@RequestParam LocalDate to,@RequestParam(defaultValue="1") @Min(1) @Max(1000000) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return service.movements(from,to,page,size); }
    @GetMapping("/dashboard") public Map<String,Object> dashboard() { return service.dashboard(); }
    @GetMapping("/reports/{type}") public List<Map<String,Object>> report(@PathVariable String type,@RequestParam LocalDate from,@RequestParam LocalDate to) { return service.report(type,from,to); }
    @GetMapping(value="/reports/{type}/csv",produces="text/csv") public ResponseEntity<String> csv(@PathVariable String type,@RequestParam LocalDate from,@RequestParam LocalDate to) {
        List<Map<String,Object>> rows=service.report(type,from,to); StringBuilder csv=new StringBuilder("\uFEFF");
        if(!rows.isEmpty()) { csv.append(String.join(",",rows.getFirst().keySet())).append("\r\n"); for(Map<String,Object> row:rows) { csv.append(row.values().stream().map(ApiController::csvCell).collect(java.util.stream.Collectors.joining(","))).append("\r\n"); } }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=inventory-"+type+".csv").body(csv.toString());
    }
    static String csvCell(Object value) { String s=Objects.toString(value,""); String leading=s.stripLeading(); if((!leading.isEmpty() && "=+@-".indexOf(leading.charAt(0))>=0) || s.startsWith("\t") || s.startsWith("\r")) s="'"+s; return "\""+s.replace("\"","\"\"")+"\""; }
}
