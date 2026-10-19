package com.inventorypro;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public final class Models {
    private Models() {}
    public record ProductInput(
        @NotBlank @Size(max=50) String sku,
        @NotBlank @Size(max=150) String name,
        @Size(max=1000) String description,
        @NotNull @Positive Long categoryId,
        @Positive Long supplierId,
        @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal unitPrice,
        @NotNull @Min(0) @Max(999999999) Long quantity,
        @NotNull @Min(0) @Max(999999999) Long reorderLevel) {}
    public record CategoryInput(@NotBlank @Size(max=100) String name, @Size(max=500) String description) {}
    public record SupplierInput(@NotBlank @Size(max=150) String name,
        @Size(max=100) String contactPerson, @Email @Size(max=150) String email,
        @Size(max=30) String phone, @Size(max=500) String address) {}
    public record MovementInput(@NotNull @Positive Long productId,
        @NotNull @Positive @Max(999999999) Long quantity,
        @NotBlank @Pattern(regexp="IN|OUT") String type,
        @NotBlank @Size(max=500) String reason) {}
    public record UserInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,50}") String username,
        @NotBlank @Size(min=12,max=72) String password,
        @NotBlank @Pattern(regexp="ADMIN|STAFF") String role) {
        @Override public String toString() { return "UserInput[username="+username+", role="+role+", password=[REDACTED]]"; }
    }
}
