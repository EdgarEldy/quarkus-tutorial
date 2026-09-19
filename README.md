# quarkus-tutorial

A complete tutorial for building a REST API with **Quarkus**, covering a full identity/RBAC domain (users, roles, permissions, tokens) and an e-commerce domain (categories, products, customers, orders) as two independent data models behind one API.

This document is the **complete specification** of the project: it is meant to be followed step by step to implement each branch.

## Table of contents

- [What is Quarkus, and how does it compare to Spring Boot 4](#what-is-quarkus-and-how-does-it-compare-to-spring-boot-4)
- [Build-time versus runtime: why Quarkus starts the way it does](#build-time-versus-runtime-why-quarkus-starts-the-way-it-does)
- [Dev Services: why there's no manual Testcontainers setup](#dev-services-why-theres-no-manual-testcontainers-setup)
- [Permission checks with @PermissionsAllowed](#permission-checks-with-permissionsallowed)
- [Configuration profiles, the Dev UI, and Continuous Testing](#configuration-profiles-the-dev-ui-and-continuous-testing)
- [Tech stack](#tech-stack)
- [Data model](#data-model)
- [Branching strategy](#branching-strategy)
- [Project structure](#project-structure)
- [Standard response format](#standard-response-format)
- [feature/core-architecture](#featurecore-architecture)
- [feature/auth](#featureauth)
- [feature/rbac](#featurerbac)
- [feature/categories](#featurecategories)
- [feature/products](#featureproducts)
- [feature/customers](#featurecustomers)
- [feature/orders](#featureorders)
- [feature/native-build (bonus)](#featurenative-build-bonus)
- [feature/reactive-endpoints (bonus)](#featurereactive-endpoints-bonus)
- [Order of work](#order-of-work)
- [Code conventions](#code-conventions)
- [Concepts covered](#concepts-covered)
- [How to follow this tutorial](#how-to-follow-this-tutorial)

## What is Quarkus, and how does it compare to Spring Boot 4

**Quarkus** is a Java framework created by Red Hat in 2019, built from the ground up for containers and Kubernetes rather than adapted to them afterward. It's marketed as "Supersonic Subatomic Java" - a nod to its two headline properties: very fast startup and a small memory footprint, achieved by doing at build time most of the work a typical framework does when the application actually boots. It reuses well-known Java standards under the hood - CDI for dependency injection, JAX-RS for REST, JPA for persistence, MicroProfile specifications for health/metrics/config - rather than inventing its own component model.

Spring Boot 4 and Quarkus solve the same class of problem (build a Java service quickly, without hand-wiring every bean) and will feel structurally familiar to anyone who knows the other: both have dependency injection, both have a curated-dependency-plus-auto-configuration mechanism (Spring Boot's *starters*, Quarkus's *extensions*), both have a live-reload dev mode, and both have an opinionated testing story. The differences are less about what each framework lets you build, and more about *when* the framework does its work and what that costs or saves you.

| | Spring Boot 4 | Quarkus |
|---|---|---|
| Dependency injection | Spring's own container, reflection-heavy, resolved mostly at runtime | CDI (via Quarkus's ArC implementation), resolved mostly at **build time** |
| How a dependency bundle works | A *starter* (`spring-boot-starter-web`) pulls in libraries; auto-configuration activates at runtime by scanning the classpath | An *extension* (`quarkus-rest`) pulls in libraries; wiring is precomputed at **build time** by the extension's own build steps |
| Startup time (typical REST service) | Roughly 1–3 seconds on the JVM | Roughly 0.05–0.5 seconds on the JVM, and well under that natively |
| Memory footprint | Higher, largely from reflection metadata kept at runtime | Lower, since most of that metadata is resolved away at build time |
| Native compilation (GraalVM) | Possible (Spring Native effort continues under Spring Boot's own native support), but bolted onto a framework not originally designed around it | A first-class target from day one - the build-time model is what makes native compilation tractable in the first place |
| Local dev database | Manual (`docker-compose up`, or a manually configured Testcontainers extension) | **Dev Services**: a database container is started automatically, with zero configuration, the moment a JDBC extension is present |
| Ecosystem size, maturity, hiring pool | Much larger - more Stack Overflow answers, more production case studies, more available developers | Smaller but growing fast, strongest specifically in the Kubernetes/serverless/cloud-native niche it was built for |
| Where each one tends to win | General-purpose enterprise applications, teams already deep in the Spring ecosystem, anything that leans on the sheer breadth of Spring's sub-projects (Batch, Integration, Modulith, Security's maturity) | Anything where startup time and memory genuinely matter at the deployment unit level: Kubernetes pods that scale to zero, AWS Lambda, CLI tools, edge/IoT workloads |

Nothing in this comparison makes one framework strictly better than the other - a Spring Boot 4 application deployed as a handful of long-running pods rarely notices a two-second startup cost, and a lot of what Quarkus optimizes for only pays off once cold-start time or memory-per-instance is actually on the critical path. This tutorial exists to teach Quarkus specifically, not to argue Spring Boot should be replaced - several of the concepts and conventions below (contract/implementation services, a generic `ApiResponse<T>`, Flyway-driven migrations) are deliberately the same ones a Spring Boot 4 project would use, so the parts that differ are the parts that are genuinely Quarkus-specific, not a wholesale reinvention of everything.

## Build-time versus runtime: why Quarkus starts the way it does

Most of what a typical framework figures out at application startup by scanning the classpath (which beans exist, which annotations apply, how dependency injection wires together), Quarkus figures out **at build time**, once, via its extension mechanism ("augmentation"). The result is bytecode that already knows its own wiring - nothing left to discover when the JVM actually boots.

This is why Quarkus applications start in milliseconds rather than seconds, why memory usage is low without any tuning, and why the same application can be compiled to a native executable (GraalVM) that starts even faster with a fraction of the memory - the build-time model is what makes native compilation *possible* at all, not just an optimization bolted onto a runtime-reflection framework afterward. `feature/native-build` in this tutorial demonstrates the difference directly, with real numbers.

## Dev Services: why there's no manual Testcontainers setup

Add `quarkus-jdbc-postgresql` to the project and run `quarkus:dev` or `quarkus:test` **without configuring a datasource URL at all**, and Quarkus notices there's no configured connection, starts a PostgreSQL container automatically (via Testcontainers under the hood), points the application at it, and tears it down when the process ends. This is **Dev Services**, and it's why this tutorial's test suite has no `@Testcontainers`/`@Container` boilerplate anywhere: the extension already does it, invisibly, for local development and for `@QuarkusTest`.

A real connection string is still configured for the packaged application (`docker-compose.yml`, production config) - Dev Services is a development/test convenience, not a replacement for a real deployment's database configuration.

## Permission checks with @PermissionsAllowed

Quarkus Security ships a declarative permission-check annotation, `@PermissionsAllowed("CATEGORY:WRITE")`, that goes further than `@RolesAllowed`: it checks whether the current `SecurityIdentity` holds a named permission, not just a role.

- A custom `SecurityIdentityAugmentor` runs once per authenticated request, after the JWT is validated: it loads the user's roles and, through them, their permissions, and adds each one to the `SecurityIdentity` as a `StringPermission`
- `@PermissionsAllowed("CATEGORY:WRITE")` on a JAX-RS resource method then simply checks whether that permission is present on the identity - no database query at the point of the check, since the augmentor already resolved everything once for the request
- This mirrors, in Quarkus's own idiom, the same underlying idea used elsewhere in this kind of system: permissions resolved once per authenticated context, then checked as a fast in-memory lookup rather than a query on every protected call

## Configuration profiles, the Dev UI, and Continuous Testing

Three everyday Quarkus mechanics this tutorial relies on throughout, worth understanding once rather than re-explaining in every branch:

- **Profiles**: `application.properties` keys can be prefixed `%dev.`, `%test.`, or `%prod.` to scope a value to one profile (`%prod.quarkus.datasource.jdbc.url=...`), with an unprefixed key acting as the default for every profile that doesn't override it. `mvn quarkus:dev` activates `dev`, `mvn test`/`@QuarkusTest` activate `test`, and a packaged jar activates `prod` unless told otherwise - this is the direct equivalent of Spring's `application-{profile}.yml` files, just expressed as prefixes inside one file rather than several separate files.
- **The Dev UI** (`http://localhost:8080/q/dev-ui` while `quarkus:dev` is running): a built-in web console listing every installed extension, letting you browse and edit configuration live, inspect Panache entities and run ad-hoc queries against them, view the endpoints exposed by `quarkus-smallrye-openapi`, and (once `feature/rbac` is in place) inspect the current `SecurityIdentity` for a request. Nothing here needs to be built by this project - it comes for free with every extension already in the [tech stack](#tech-stack).
- **Continuous Testing**: pressing `r` in the terminal running `mvn quarkus:dev` re-runs the test suite affected by the files just changed, on every save, without restarting the application. Combined with Dev Services (so the test database is already there), the practical effect is that `feature/*` branches in this tutorial can be developed with tests running continuously in the background rather than triggered by hand after each change.

## Tech stack

| Component | Choice |
|---|---|
| Framework | Quarkus 3.33.x (LTS stream) |
| Language | Java 21 (LTS) |
| Build | Maven |
| REST layer | Quarkus REST (`quarkus-rest`, `quarkus-rest-jackson`) - the current name for what was previously branded "RESTEasy Reactive" |
| Database | PostgreSQL 16 (Dev Services in dev/test, Docker Compose for the packaged app) |
| ORM | Hibernate ORM with Panache, repository pattern (`PanacheRepository<T>`) |
| Migrations | Flyway (`quarkus-flyway`) |
| Validation | Hibernate Validator (`quarkus-hibernate-validator`) |
| Security | `quarkus-smallrye-jwt` (token issuance/validation), `quarkus-security` (`@RolesAllowed`, `@PermissionsAllowed`) |
| API documentation | `quarkus-smallrye-openapi` (Swagger UI) |
| Error handling | `quarkus-http-problem` (Quarkiverse, RFC 9457 Problem Details) |
| Monitoring | `quarkus-smallrye-health` (custom `@Liveness`/`@Readiness` checks) |
| Caching | `quarkus-cache` (`@CacheResult`/`@CacheInvalidate`) |
| Scheduling | `quarkus-scheduler` (`@Scheduled`) |
| Reactive programming *(bonus)* | SmallRye Mutiny (`Uni`/`Multi`), `quarkus-hibernate-reactive-panache`, `quarkus-reactive-pg-client` |
| Tests | `quarkus-junit5`, RestAssured, Dev Services (no manual Testcontainers setup) |
| CI/CD | GitHub Actions |
| Containerization | Docker, docker-compose; GraalVM native image (bonus branch) |

## Data model

Two independent domains, one shared database, no cross-domain foreign key.

```
users (id, first_name, last_name, email, password, enabled, account_locked)
    │ N──N (via role_user)
roles (id, role_name)
    │ N──N (via role_permission)
permissions (id, resource, action)

activation_tokens (id, user_id, token, created_at, expires_at, validated_at)
blacklisted_tokens (id, user_id, token, jti, blacklisted_at, created_at, expires_at, validated_at)
password_reset_tokens (id, user_id, token, type, expiry_date)
audit_logs (id, actor_user_id, action, entity_type, entity_id, details, created_at)

categories (id, category_name)
    │ 1
    │
    │ N
products (id, category_id, product_name, unit_price)
    │ 1
    │
    │ N
orders (id, customer_id, product_id, quantity, total)
    │ N
    │
    │ 1
customers (id, first_name, last_name, telephone, email, address)
```

## Branching strategy

| Branch | Role |
|---|---|
| `master` | Stable, production-ready code. No direct commits, only merges from `develop`. |
| `develop` | Integration branch. |
| `feature/core-architecture` | Project structure, Flyway/PostgreSQL configuration, exception mapping, Docker, CI. |
| `feature/auth` | User entity, registration, activation, login, JWT issuance, logout (blacklist), password reset. |
| `feature/rbac` | Roles/permissions, `SecurityIdentityAugmentor`, `@PermissionsAllowed` wiring, administration endpoints. |
| `feature/categories` | Category CRUD. |
| `feature/products` | Product CRUD, depends on `categories`. |
| `feature/customers` | Customer CRUD. |
| `feature/orders` | Order CRUD, depends on `products`/`customers`. |
| `feature/native-build` | *Bonus*: GraalVM native compilation, startup/memory comparison against JVM mode. |

## Project structure

```
quarkus-tutorial/
├── src/
│   ├── main/
│   │   ├── java/com/edgareldy/quarkustutorial/
│   │   │   ├── entity/
│   │   │   │   ├── User.java, Role.java, Permission.java
│   │   │   │   ├── ActivationToken.java, BlacklistedToken.java, PasswordResetToken.java
│   │   │   │   ├── Category.java, Product.java, Customer.java, Order.java
│   │   │   ├── repository/
│   │   │   │   ├── UserRepository.java, RoleRepository.java, PermissionRepository.java
│   │   │   │   ├── CategoryRepository.java, ProductRepository.java,
│   │   │   │   │   CustomerRepository.java, OrderRepository.java
│   │   │   │   └── (each: interface extending PanacheRepository<T>, plus a contract for custom query methods)
│   │   │   ├── dto/
│   │   │   │   ├── common/ (ApiResponse.java, PageResponse.java)
│   │   │   │   ├── auth/ (RegisterRequest, LoginRequest, AuthResponse, ...)
│   │   │   │   ├── rbac/ (RoleRequest, RoleResponse, PermissionRequest, PermissionResponse)
│   │   │   │   └── ecommerce/ (CategoryRequest/Response, ProductRequest/Response, ...)
│   │   │   ├── service/
│   │   │   │   ├── AuthService.java, RbacService.java, CategoryService.java, ProductService.java,
│   │   │   │   │   CustomerService.java, OrderService.java   (contracts)
│   │   │   │   └── impl/ (one *ServiceImpl per interface, @ApplicationScoped)
│   │   │   ├── resource/
│   │   │   │   ├── AuthResource.java
│   │   │   │   ├── UserResource.java, RoleResource.java, PermissionResource.java
│   │   │   │   ├── CategoryResource.java, ProductResource.java,
│   │   │   │   │   CustomerResource.java, OrderResource.java
│   │   │   ├── security/
│   │   │   │   ├── JwtIssuer.java
│   │   │   │   └── PermissionSecurityIdentityAugmentor.java
│   │   │   └── exception/
│   │   │       ├── ResourceNotFoundException.java   (extends HttpProblem)
│   │   │       └── BusinessRuleException.java        (extends HttpProblem)
│   │   └── resources/
│   │       ├── application.properties
│   │       └── db/migration/
│   │           └── V1__init_schema.sql
│   └── test/
│       └── java/com/edgareldy/quarkustutorial/
│           ├── resource/    (@QuarkusTest + RestAssured)
│           ├── service/     (Mockito)
│           └── repository/  (@QuarkusTest, Dev Services-backed PostgreSQL, no manual container setup)
├── docker-compose.yml
├── Dockerfile.jvm
├── Dockerfile.native
├── .github/workflows/ci.yml
├── pom.xml
└── README.md
```

## Standard response format

This project splits success and error responses the same way rather than forcing one shape to do both jobs:

**Success**: every 2xx response wraps its payload in a generic `ApiResponse<T>`.

```java
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp
) {
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, data, Instant.now());
    }
}
```

**Errors**: every non-2xx response is a **Problem Details** document (RFC 9457, which obsoletes RFC 7807 - same `application/problem+json` wire format), produced by the `quarkus-http-problem` Quarkiverse extension rather than a hand-written `ExceptionMapper`.

- `ResourceNotFoundException` and `BusinessRuleException` extend `io.quarkiverse.httpproblem.HttpProblem` directly and are thrown normally from a service method - `HttpProblem` already *is* a `RuntimeException`, built via its own builder (`.withTitle(...)`, `.withStatus(...)`, `.withDetail(...)`), so there's no separate DTO and no manual mapping step for these two
- `quarkus-http-problem` ships its own built-in mappers for the exceptions a project doesn't throw on purpose: Bean Validation failures become a Problem with a `violations` extension listing each field, and anything unmapped becomes a generic 500 Problem with implementation details stripped out before the response is sent - both without a single line of this project's own code
- A response's `type`/`title`/`status`/`detail` fields carry what an `ApiResponse<Void>` used to carry in `message`, and any structured, field-level detail (like a validation error list) rides in the same document's `extensions` rather than a nested `data`

Example success response:

```json
{
  "success": true,
  "message": "Product created successfully",
  "data": { "id": 12, "productName": "Mechanical keyboard", "unitPrice": 79.99 },
  "timestamp": "2026-09-18T10:15:30Z"
}
```

Example error response (`GET /api/v1/products/999` on a missing product):

```json
{
  "type": "https://api.example.com/problems/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Product 999 not found",
  "instance": "/api/v1/products/999"
}
```

## feature/core-architecture

### Tasks

- [x] Generate the project (`mvn io.quarkus:quarkus-maven-plugin:create`, or code.quarkus.io), Java 21, Maven
- [x] Extensions: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-orm-panache`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-hibernate-validator`, `quarkus-smallrye-jwt`, `quarkus-smallrye-jwt-build`, `quarkus-security`, `quarkus-smallrye-openapi`, `quarkus-smallrye-health`, `quarkus-cache`, `quarkus-scheduler`, `io.quarkiverse.httpproblem:quarkus-http-problem`
- [x] A custom `@Readiness` check (`DatabaseHealthCheck`, verifying a real connection can be obtained) alongside the default one `quarkus-smallrye-health` already provides, and a custom `@Liveness` check confirming the application isn't in a stuck state - both visible at `/q/health`, and individually at `/q/health/ready`/`/q/health/live`
- [x] Test extensions: `quarkus-junit`, `quarkus-junit-mockito`, `rest-assured`
- [x] `ApiResponse<T>`, `PageResponse<T>`
- [x] `ResourceNotFoundException`, `BusinessRuleException` (both extending `HttpProblem`, built via its builder with the appropriate status/title); the problem `type` URI is a constant on each exception (this version of `quarkus-http-problem` has no type prefix option) and stack traces are never included in a response, in any profile
- [x] Flyway script `V1__init_schema.sql` (all tables from both domains)
- [x] `application.properties`: JWT signing key location, Flyway enabled, `%test`/`%dev` profiles left without a configured datasource (Dev Services provisions PostgreSQL automatically), `%prod` profile with a real connection string
- [x] `docker-compose.yml` (app + PostgreSQL, for the packaged application only - not used in dev/test), `Dockerfile.jvm`
- [x] `.github/workflows/ci.yml`: `mvn verify` (Dev Services provisions PostgreSQL inside the CI runner automatically, same as locally)

## feature/auth

### Endpoints

| Method | URL | Description |
|---|---|---|
| POST | `/api/v1/auth/register` | Register (creates a disabled user + activation token) |
| GET | `/api/v1/auth/activate-account` | Activates a user account |
| POST | `/api/v1/auth/login` | Returns a JWT |
| POST | `/api/v1/auth/logout` | Blacklists the current JWT |
| GET | `/api/v1/auth/me` | Current user profile |
| POST | `/api/v1/auth/forgot-password` | Generates a password-reset token |
| POST | `/api/v1/auth/reset-password` | Consumes the token, updates the password |

### Tasks

- [x] `User` entity, `ActivationToken`, `BlacklistedToken`, `PasswordResetToken`
- [x] `UserRepository` (`PanacheRepository<User>`)
- [x] `AuthService` (interface) + implementation: registration, activation, login (password hashing/verification via `io.quarkus.elytron.security.common.BcryptUtil.bcryptHash`/`matches`, from `quarkus-elytron-security-common`, a Quarkus module declared explicitly in the pom because `quarkus-security` does not bring it in, not an external library), logout, forgot/reset password
- [x] `forgotPassword` returns the exact same response - same status code, same body, roughly the same timing (no early return skipping the token-generation work) - whether or not the submitted email matches an existing account, so the endpoint can't be used to enumerate registered emails
- [x] `JwtIssuer`: builds a signed JWT (`io.smallrye.jwt.build.Jwt`) with a unique `jti` claim, the user's id as subject
- [x] `@SecurityScheme(securitySchemeName = "jwt", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")` declared once (e.g. on `AuthResource` or a dedicated `OpenApiConfig` class) and referenced via `@SecurityRequirement(name = "jwt")` on protected resources, so Swagger UI's "Authorize" button actually works against `@PermissionsAllowed`-protected endpoints
- [x] A `ContainerRequestFilter` (or `SecurityIdentityAugmentor`, same class introduced fully in `feature/rbac`) checking the incoming JWT's `jti` against `BlacklistedToken` and rejecting the request if found
- [x] `AuthResource`
- [x] Tests (`@QuarkusTest` + RestAssured): register → activate → login → access `/me`, logout followed by a rejected request with the same token, forgot/reset password flow

## feature/rbac

Full CRUD for users, roles, and permissions. Assignments always flow in one direction, never the other: **permissions are assigned onto a role**, never the reverse (a `Permission` is a fixed, catalog-like thing - `CATEGORY:WRITE` always means the same thing - a `Role` is what gets composed out of permissions); **roles are assigned onto a user**, never the reverse (a `Role` is the fixed thing here, a `User` is what's being granted access). Neither `PermissionResource` nor `RoleResource` ever gains an endpoint that manages the relationship from the other side.

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/users` | Paginated list | `@PermissionsAllowed("USER:READ")` |
| GET | `/api/v1/users/{id}` | Detail, including assigned roles | `@PermissionsAllowed("USER:READ")` |
| PATCH | `/api/v1/users/{id}/roles/{roleId}` | Assign a role to a user | `@PermissionsAllowed("USER:WRITE")` |
| DELETE | `/api/v1/users/{id}/roles/{roleId}` | Remove a role from a user | `@PermissionsAllowed("USER:WRITE")` |
| GET | `/api/v1/roles` | List, including permissions | `@PermissionsAllowed("ROLE:READ")` |
| POST | `/api/v1/roles` | Create | `@PermissionsAllowed("ROLE:WRITE")` |
| PUT | `/api/v1/roles/{id}` | Update (name only) | `@PermissionsAllowed("ROLE:WRITE")` |
| DELETE | `/api/v1/roles/{id}` | Delete | `@PermissionsAllowed("ROLE:WRITE")` |
| POST | `/api/v1/roles/{id}/permissions/{permissionId}` | Assign a permission to a role | `@PermissionsAllowed("ROLE:WRITE")` |
| DELETE | `/api/v1/roles/{id}/permissions/{permissionId}` | Remove a permission from a role | `@PermissionsAllowed("ROLE:WRITE")` |
| GET | `/api/v1/permissions` | List | `@PermissionsAllowed("PERMISSION:READ")` |
| POST | `/api/v1/permissions` | Create | `@PermissionsAllowed("PERMISSION:WRITE")` |
| PUT | `/api/v1/permissions/{id}` | Update | `@PermissionsAllowed("PERMISSION:WRITE")` |
| DELETE | `/api/v1/permissions/{id}` | Delete | `@PermissionsAllowed("PERMISSION:WRITE")` |

### Tasks

- [x] `Role`, `Permission`, `AuditLog` entities, `RoleRepository`, `PermissionRepository`, `AuditLogRepository`
- [x] `RbacService` (interface) + implementation:
  - `createRole`/`updateRole`/`deleteRole` - `deleteRole` rejects if any user is still assigned this role (must be unassigned first)
  - `createPermission`/`updatePermission`/`deletePermission` - `deletePermission` rejects if any role still has this permission assigned
  - `assignPermissionToRole`/`removePermissionFromRole` - `removePermissionFromRole` rejects removing `ROLE:WRITE` from a role if doing so would leave **zero** users anywhere holding a role that grants `ROLE:WRITE`
  - `assignRoleToUser`/`removeRoleFromUser` - `removeRoleFromUser` applies the same last-admin check at the point of removal from a specific user, so a self-lockout is caught however it's attempted
- [x] `AuditLogger`: a single `log(String action, String entityType, Long entityId, String details)` method, called from every method above - every RBAC mutation is traceable (who did what, to what, when), not just the happy-path ones
- [x] `PermissionSecurityIdentityAugmentor` (`SecurityIdentityAugmentor`): after JWT validation, loads the user's roles and permissions, adds each `RESOURCE:ACTION` string to the identity as a `StringPermission`
- [x] `UserResource`, `RoleResource`, `PermissionResource`, each method annotated `@PermissionsAllowed("...")` as listed above - `UserResource` only manages role assignment on existing users, it never creates a user directly (registration stays exclusively `feature/auth`'s job)
- [x] A seeding step (a `@Observes StartupEvent` method, or a Flyway data-migration): baseline permissions covering every resource/action this project defines, assigned to a seeded `ADMIN` role - without this, nobody could ever be granted `ROLE:WRITE`/`PERMISSION:WRITE` to create the first assignment (done as the Flyway migration `V2__seed_baseline_rbac.sql`; an optional `AdminBootstrap` startup observer, active only when `app.bootstrap-admin.email` and `app.bootstrap-admin.password` are set and given dev-only defaults in `%dev`, creates the first administrator, since nobody can otherwise hold `ADMIN`)
- [x] `ExpiredTokenCleanupJob` (`@Scheduled(cron = "0 0 3 * * ?")`, `quarkus-scheduler`): daily job deleting `BlacklistedToken`/`ActivationToken`/`PasswordResetToken` rows past their expiry, so both tables stay bounded over time - revocation and expiry checks never depend on the row still existing, so deleting it later is purely housekeeping, not a correctness concern
- [x] Tests: full CRUD on roles and permissions, the "still referenced" rejection on both `deleteRole` and `deletePermission`, the augmentor granting the expected permissions for a multi-role user, a `@PermissionsAllowed`-protected endpoint accepting/rejecting correctly, and specifically the last-admin rejection triggered both ways (removing the role from the last user who has it, and removing the permission from the role that was their only source of it), plus an assertion that every mutation above produces a matching `AuditLog` row

## feature/categories

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/categories` | Paginated list | `@PermissionsAllowed("CATEGORY:READ")` |
| GET | `/api/v1/categories/{id}` | Detail | `@PermissionsAllowed("CATEGORY:READ")` |
| POST | `/api/v1/categories` | Create | `@PermissionsAllowed("CATEGORY:WRITE")` |
| PUT | `/api/v1/categories/{id}` | Update | `@PermissionsAllowed("CATEGORY:WRITE")` |
| DELETE | `/api/v1/categories/{id}` | Delete | `@PermissionsAllowed("CATEGORY:WRITE")` |

### Tasks

- [x] `Category` entity, repository, contract/implementation service
- [x] Business rule: deleting a category that still has products is rejected (`BusinessRuleException` → 422)
- [x] `CategoryResource`
- [x] Tests for every endpoint, including the rejection case and a permission-denied case

## feature/products

Depends on `feature/categories` existing, since every product references one.

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/products` | Paginated list, filterable by `categoryId` | `@PermissionsAllowed("PRODUCT:READ")` |
| GET | `/api/v1/products/{id}` | Detail | `@PermissionsAllowed("PRODUCT:READ")` |
| POST | `/api/v1/products` | Create | `@PermissionsAllowed("PRODUCT:WRITE")` |
| PUT | `/api/v1/products/{id}` | Update | `@PermissionsAllowed("PRODUCT:WRITE")` |
| DELETE | `/api/v1/products/{id}` | Delete | `@PermissionsAllowed("PRODUCT:WRITE")` |

### Tasks

- [ ] `Product` entity, repository, contract/implementation service
- [ ] `ProductService.findById` annotated `@CacheResult(cacheName = "product-cache")`; `ProductServiceImpl.update`/`delete` annotated `@CacheInvalidate(cacheName = "product-cache")` on the same key - a product read hits Panache once and the cache for every subsequent read until it's changed, and every write explicitly clears its own entry rather than leaving a stale cached value silently served
- [ ] `ProductResource`
- [ ] Tests, including the category filter, a permission-denied case, and a cache test asserting a second read doesn't hit the repository while an update correctly invalidates the entry

## feature/customers

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/customers` | Paginated list | `@PermissionsAllowed("CUSTOMER:READ")` |
| GET | `/api/v1/customers/{id}` | Detail | `@PermissionsAllowed("CUSTOMER:READ")` |
| POST | `/api/v1/customers` | Create | `@PermissionsAllowed("CUSTOMER:WRITE")` |
| PUT | `/api/v1/customers/{id}` | Update | `@PermissionsAllowed("CUSTOMER:WRITE")` |
| DELETE | `/api/v1/customers/{id}` | Delete | `@PermissionsAllowed("CUSTOMER:WRITE")` |

### Tasks

- [ ] `Customer` entity, repository, contract/implementation service
- [ ] `CustomerResource`
- [ ] Tests

## feature/orders

### Endpoints

| Method | URL | Description | Access |
|---|---|---|---|
| GET | `/api/v1/orders` | Paginated list, filterable by `customerId`/`productId` | `@PermissionsAllowed("ORDER:READ")` |
| GET | `/api/v1/orders/{id}` | Detail | `@PermissionsAllowed("ORDER:READ")` |
| POST | `/api/v1/orders` | Create (computes `total`) | `@PermissionsAllowed("ORDER:WRITE")` |

### Tasks

- [ ] `Order` entity, repository, contract/implementation service: computes `total = quantity * product.unitPrice`
- [ ] `OrderResource`
- [ ] Tests, including the total computation

## feature/native-build (bonus)

### Tasks

- [ ] `Dockerfile.native` (multi-stage: GraalVM builder image, minimal runtime image)
- [ ] Build a native executable locally (`mvn package -Dnative -Dquarkus.native.container-build=true`, container build so a local GraalVM install isn't required)
- [ ] Measure and document, in this branch's own notes: startup time and resident memory of the JVM-mode application versus the native executable, under the same request load
- [ ] `.github/workflows/ci.yml` extended with a native build verification job (can be slower/separate from the main test job)

## feature/reactive-endpoints (bonus)

Quarkus lets imperative (blocking, Panache-based) and reactive (Mutiny-based) code coexist in the same application, on the same underlying Vert.x event loop - unlike stacks where the two styles require committing to an entirely separate framework variant for the whole application. This branch adds exactly one reactive slice to an otherwise fully imperative project, to demonstrate that coexistence concretely rather than as a claim.

### Endpoints

| Method | URL | Description |
|---|---|---|
| GET | `/api/v1/orders/stream` | Server-Sent Events stream of newly created orders |

### Tasks

- [ ] `quarkus-hibernate-reactive-panache`, `quarkus-reactive-pg-client` added alongside the existing blocking `quarkus-jdbc-postgresql` stack - both drivers coexist against the same PostgreSQL database, Dev Services provisions the container either way
- [ ] `OrderPanacheRepositoryReactive` (`PanacheRepositoryBase` from the reactive variant), used only by the streaming endpoint - the rest of `feature/orders`' blocking repository and service stay untouched
- [ ] A CDI event or a `Multi`-backed broadcast: `OrderServiceImpl.create` (blocking) publishes a plain CDI event after a successful commit; a reactive listener adapts that into a `Multi<Order>` that the streaming endpoint subscribes to
- [ ] `OrderResource.stream()`: returns `Multi<Order>` with `@Produces(MediaType.SERVER_SENT_EVENTS)` - no manual thread management, Mutiny's operators handle backpressure
- [ ] Tests: a `Multi` assertion subscriber (`AssertSubscriber`) verifying an event arrives on the stream after a `POST /api/v1/orders`, run without blocking the test thread

## Order of work

1. `feature/core-architecture` → Pull Request to `develop`
2. `feature/auth` (depends on `core-architecture`) → Pull Request to `develop`
3. `feature/rbac` (depends on `auth`) → Pull Request to `develop`
4. `feature/categories` (depends on `rbac`) → Pull Request to `develop`
5. `feature/products` (depends on `categories`) → Pull Request to `develop`
6. `feature/customers` (depends on `rbac`) → Pull Request to `develop`
7. `feature/orders` (depends on `products`, `customers`) → Pull Request to `develop`
8. `feature/native-build` (bonus, depends on everything above) → Pull Request to `develop`
9. `feature/reactive-endpoints` (bonus, depends on `feature/orders`, independent of `feature/native-build`) → Pull Request to `develop`
10. `develop` → `master`

## Code conventions

- Root package: `com.edgareldy.quarkustutorial`
- **Contract/implementation services**: interface at the root of `service/`, implementation in `service/impl/`, annotated `@ApplicationScoped`
- Repositories extend `PanacheRepository<T>` directly for standard operations; a custom interface is added only when a repository needs a query beyond what Panache already provides
- Every endpoint returns an `ApiResponse<T>` (or `ApiResponse<PageResponse<T>>` for lists)
- Permission checks are always declarative (`@PermissionsAllowed("RESOURCE:ACTION")` on the resource method), never a manual `if` inside a method body
- No JAX-RS resource method contains business logic - it validates via Bean Validation annotations on the request DTO, delegates to a service, and maps the result to an `ApiResponse<T>`

## Concepts covered

- Quarkus's build-time augmentation model, and why it enables both fast startup and native compilation
- Configuration profiles (`%dev`/`%test`/`%prod`), the Dev UI, and Continuous Testing
- Dev Services: automatic, zero-configuration database provisioning for development and tests
- Quarkus REST (JAX-RS) resources, request/response mapping, Bean Validation
- Hibernate ORM with Panache, repository pattern
- JWT issuance and validation with SmallRye JWT
- Token revocation via a blacklist checked on every request
- Declarative permission checks with `@PermissionsAllowed`, backed by a custom `SecurityIdentityAugmentor`
- RFC 9457 Problem Details error responses (`quarkus-http-problem`), kept separate from the `ApiResponse<T>` success envelope
- Generic `ApiResponse<T>` DTO, contract/implementation pattern
- Custom health checks (`@Liveness`/`@Readiness`) alongside MicroProfile Health's defaults
- Method-level caching (`@CacheResult`/`@CacheInvalidate`) and explicit invalidation on write
- Scheduled background jobs (`@Scheduled`) for housekeeping tasks
- Testing with `@QuarkusTest` and RestAssured, without manual Testcontainers setup
- GraalVM native compilation, and measuring its actual impact on startup/memory (bonus)
- Reactive programming with Mutiny (`Uni`/`Multi`), Server-Sent Events, and imperative/reactive coexistence in one application (bonus)
- Containerization (Docker, docker-compose)
- Continuous integration (GitHub Actions)

## How to follow this tutorial

1. Clone the repository : https://github.com/EdgarEldy/quarkus-tutorial.git and check out `develop`
2. Follow the branches in order: `feature/core-architecture` → `feature/auth` → `feature/rbac` → `feature/categories` → `feature/products` → `feature/customers` → `feature/orders` → (bonus) `feature/native-build` → (bonus) `feature/reactive-endpoints`
3. Run in dev mode: `mvn quarkus:dev` (Dev Services starts PostgreSQL automatically, no `docker-compose up` needed for this) - open the Dev UI at `http://localhost:8080/q/dev-ui` alongside it, and press `r` in the terminal to trigger Continuous Testing
4. Browse Swagger UI at `http://localhost:8080/q/swagger-ui`
5. To run the packaged application instead: `docker-compose up`