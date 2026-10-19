package com.inventorypro;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.core.env.Environment;
import java.util.*;

@RestControllerAdvice
public class ApiErrors {
    private final Environment env;
    public ApiErrors(Environment env) { this.env=env; }
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",Objects.toString(e.getReason(),"Request failed"))); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
        Map<String,String> fields=new LinkedHashMap<>(); e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(),f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("message","Please check the form fields","fields",fields));
    }
    @ExceptionHandler({ConstraintViolationException.class,org.springframework.web.method.annotation.HandlerMethodValidationException.class,org.springframework.http.converter.HttpMessageNotReadableException.class,org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,org.springframework.web.bind.MissingServletRequestParameterException.class})
    ResponseEntity<?> badInput(Exception e) { return ResponseEntity.badRequest().body(Map.of("message","Invalid or missing request values")); }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<?> integrity(DataIntegrityViolationException e) {
        String message="A unique value already exists, a referenced category/supplier is missing, or this record has dependent products or stock history. Check the values and relationships.";
        return ResponseEntity.status(409).body(Map.of("message",message));
    }
    @ExceptionHandler({CannotAcquireLockException.class,org.springframework.transaction.TransactionTimedOutException.class}) ResponseEntity<?> lock(Exception e) { return ResponseEntity.status(409).body(Map.of("message","Inventory is being updated. Please retry.")); }
    @ExceptionHandler(DataAccessException.class) ResponseEntity<?> database(DataAccessException e) { return ResponseEntity.status(503).body(Map.of("message",env.matchesProfiles("demo") ? "Demo database is unavailable. Please retry shortly." : "Oracle database is unavailable or not initialized. Check connection settings and schema.")); }
}
