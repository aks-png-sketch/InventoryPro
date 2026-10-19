# Architecture

```mermaid
flowchart LR
    Browser[React + Tailwind UI] -->|same-origin /api| Vite[Vite development proxy]
    Vite --> Security[Spring Security: session + CSRF + roles]
    Security --> API[REST controllers + Bean Validation]
    API --> Service[InventoryService]
    Service --> JDBC[JdbcTemplate + transaction manager]
    JDBC --> Pool[HikariCP + Oracle Thin driver]
    Pool --> Oracle[(Oracle XEPDB1 / INVENTORY_APP)]
    Demo[Standalone JdbcDemo] -->|DriverManager + PreparedStatement| Oracle
```

```mermaid
erDiagram
    USERS ||--o{ STOCK_MOVEMENTS : records
    CATEGORIES ||--o{ PRODUCTS : categorizes
    SUPPLIERS o|--o{ PRODUCTS : supplies
    PRODUCTS ||--o{ STOCK_MOVEMENTS : tracks
    USERS {
        NUMBER id PK
        VARCHAR2 username UK
        VARCHAR2 password_hash
        VARCHAR2 role
        TIMESTAMP created_at
    }
    CATEGORIES {
        NUMBER id PK
        VARCHAR2 name UK
        VARCHAR2 description
    }
    SUPPLIERS {
        NUMBER id PK
        VARCHAR2 name UK
        VARCHAR2 contact_person
        VARCHAR2 email
        VARCHAR2 phone
        VARCHAR2 address
    }
    PRODUCTS {
        NUMBER id PK
        VARCHAR2 sku UK
        VARCHAR2 name
        NUMBER category_id FK
        NUMBER supplier_id FK
        NUMBER unit_price
        NUMBER quantity
        NUMBER reorder_level
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    STOCK_MOVEMENTS {
        NUMBER id PK
        NUMBER product_id FK
        NUMBER user_id FK
        VARCHAR2 movement_type
        NUMBER quantity
        NUMBER balance_after
        VARCHAR2 reason
        TIMESTAMP created_at
    }
```

Login uses Spring Security's authentication filter and BCrypt-backed UserDetailsService querying Oracle. Success creates/rotates the session; the frontend then fetches a new CSRF token and session identity. Reloading the page restores identity from `/auth/me`. Logout invalidates the session. Routes are gated by identity in React and independently protected by Spring Security and method authorization.

The service layer owns transactions. The database is the authority for IDs, constraints and balances. Product lists and stock history are paginated. Search uses bound parameters and escapes LIKE wildcards; sort names/directions are allowlisted before composing SQL. Reports are read-only SQL projections, exported with spreadsheet-formula escaping. Database errors are translated to meaningful HTTP errors without exposing raw SQL or credentials.
