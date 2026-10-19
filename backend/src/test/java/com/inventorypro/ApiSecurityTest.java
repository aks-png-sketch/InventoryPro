package com.inventorypro;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@WebMvcTest(value=ApiController.class,properties="FRONTEND_ORIGINS=https://inventory.example")
@Import(SecurityConfig.class)
class ApiSecurityTest {
    @Test @WithMockUser(roles="ADMIN") void fractionalQuantityRejectedInsteadOfTruncated() throws Exception {mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT.replace("\"quantity\":0","\"quantity\":1.5"))).andExpect(status().isBadRequest());verifyNoInteractions(service);}
    @Test void frontendAssetsAreNotBlockedByAuthentication() throws Exception {mvc.perform(get("/assets/nonexistent.js")).andExpect(status().isNotFound());}
    @Test void untrustedCorsOriginRejected() throws Exception {mvc.perform(options("/api/products").header("Origin","https://evil.example").header("Access-Control-Request-Method","POST")).andExpect(status().isForbidden());}
    @Test void explicitCorsOriginAllowed() throws Exception {mvc.perform(options("/api/products").header("Origin","https://inventory.example").header("Access-Control-Request-Method","POST")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","https://inventory.example")).andExpect(header().string("Access-Control-Allow-Credentials","true"));}
    @Autowired MockMvc mvc;
    @MockitoBean InventoryService service;
    @MockitoBean JdbcTemplate jdbc;
    static final String PRODUCT="""
        {"sku":"TEST-1","name":"Test product","categoryId":1,"supplierId":null,"unitPrice":10.25,"quantity":0,"reorderLevel":5}
        """;
    @Test void unauthenticatedRequestRejected() throws Exception {mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());}
    @Test void csrfTokenPubliclyAvailable() throws Exception {mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());}
    @Test @WithMockUser(roles="ADMIN") void missingCsrfRejected() throws Exception {mvc.perform(post("/api/products").contentType(APPLICATION_JSON).content(PRODUCT)).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test @WithMockUser(roles="STAFF") void staffCannotCreateProduct() throws Exception {mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT)).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test @WithMockUser(roles="ADMIN") void createProduct() throws Exception {when(service.createProduct(any(),anyString())).thenReturn(Map.of("id",1));mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT)).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));}
    @Test @WithMockUser(roles="ADMIN") void updateProduct() throws Exception {when(service.updateProduct(eq(1L),any())).thenReturn(Map.of("id",1));mvc.perform(put("/api/products/1").with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT)).andExpect(status().isOk());verify(service).updateProduct(eq(1L),any());}
    @Test @WithMockUser(roles="ADMIN") void deleteProduct() throws Exception {mvc.perform(delete("/api/products/1").with(csrf())).andExpect(status().isNoContent());verify(service).deleteProduct(1);}
    @Test @WithMockUser(roles="ADMIN") void invalidProductRejected() throws Exception {mvc.perform(post("/api/products").with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT.replace("10.25","-1"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.unitPrice").exists());verifyNoInteractions(service);}
    @Test @WithMockUser(roles="STAFF") void staffCanMoveStock() throws Exception {when(service.move(any(),anyString())).thenReturn(Map.of("quantity",4));mvc.perform(post("/api/movements").with(csrf()).contentType(APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1,\"type\":\"OUT\",\"reason\":\"Dispatch\"}")).andExpect(status().isCreated());}
    @Test @WithMockUser(roles="STAFF") void dashboardReadAllowed() throws Exception {when(service.dashboard()).thenReturn(Map.of("totalProducts",3,"inventoryValue",100));mvc.perform(get("/api/dashboard")).andExpect(status().isOk()).andExpect(jsonPath("$.totalProducts").value(3));}
    @Test @WithMockUser(roles="STAFF") void invalidPaginationRejected() throws Exception {mvc.perform(get("/api/products?page=0")).andExpect(status().isBadRequest());}
    @Test @WithMockUser(roles="STAFF") void sessionIdentityAvailable() throws Exception {mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STAFF"));}
    @Test @WithMockUser(roles="STAFF") void logoutRequiresCsrf() throws Exception {mvc.perform(post("/api/auth/logout")).andExpect(status().isForbidden());mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());}
}
