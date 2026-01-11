# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a **legacy code review exercise** - a Spring Boot 3.5.x REST API for product management, intentionally written with common anti-patterns and code quality issues. The codebase serves as a code review assignment where reviewers identify and document problems following a specific format.

**Important Context**: This code contains intentional issues for educational purposes. There are 55 documented code review points covering REST API design, JPA optimization, exception handling, validation, and Spring best practices.

## Technology Stack

- **Java 17** with Spring Boot 3.5.7
- **Spring Web** - REST API endpoints
- **Spring Data JPA** with Hibernate
- **H2 Database** (in-memory) - development/testing
- **Lombok 1.18.26** - boilerplate reduction
- **Gradle 8.x** - build tool

## Development Commands

### Build and Run
```bash
# Build the project
./gradlew build

# Run the application (port 8080)
./gradlew bootRun

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests com.wjc.codetest.CodeTestApplicationTests
```

### Database Access
- **H2 Console**: http://localhost:8080/h2-console
- **JDBC URL**: `jdbc:h2:mem:codetest`
- **Username**: `sa`
- **Password**: (empty)

### Gradle Tasks
```bash
# Clean build directory
./gradlew clean

# Show dependencies
./gradlew dependencies

# Check for dependency updates
./gradlew dependencyUpdates
```

## Architecture Overview

### Package Structure
```
com.wjc.codetest/
├── GlobalExceptionHandler.java          # Global exception handling
├── CodeTestApplication.java             # Spring Boot entry point
└── product/
    ├── controller/
    │   └── ProductController.java       # REST endpoints
    ├── service/
    │   └── ProductService.java          # Business logic
    ├── repository/
    │   └── ProductRepository.java       # JPA repository
    └── model/
        ├── domain/
        │   └── Product.java             # JPA entity
        ├── request/
        │   ├── CreateProductRequest.java
        │   ├── UpdateProductRequest.java
        │   └── GetProductListRequest.java
        └── response/
            └── ProductListResponse.java
```

### Layer Responsibilities

**Controller Layer** (`ProductController.java`):
- REST endpoint definitions
- HTTP method mapping (GET/POST)
- Request/response handling
- Currently exposes JPA entities directly (intentional issue)

**Service Layer** (`ProductService.java`):
- Business logic execution
- Transaction boundaries (missing `@Transactional` - intentional issue)
- Entity orchestration
- Optional handling

**Repository Layer** (`ProductRepository.java`):
- JPA data access
- Custom JPQL queries
- Extends `JpaRepository<Product, Long>`

### Known Architectural Issues (By Design)

The codebase intentionally violates several best practices:

1. **Entity Exposure**: Controllers return `Product` entities directly instead of DTOs
2. **Missing Validation**: No `@Valid` annotations on request bodies
3. **Wrong HTTP Methods**: DELETE operations use POST, updates use POST instead of PATCH/PUT
4. **No Transaction Management**: Service methods lack `@Transactional` annotations
5. **Generic Exception Handling**: Uses `RuntimeException` instead of custom exceptions
6. **Optional Anti-patterns**: Uses `isPresent() + get()` instead of `orElseThrow()`
7. **JPA Inefficiencies**: Unnecessary `save()` calls, redundant queries before delete

## Code Review Format

All code reviews in this project follow this structure:
```
[리뷰 번호 - 제목]
문제: What problem was observed
원인: Root cause in code/query/design
개선안: Alternative solutions, tradeoffs, rationale

전/후 비교:
  Before: Current implementation
  After:  Improved implementation

측정치: Performance metrics, query reduction, response times

참고 링크: Official docs, RFCs, technical references
```

See `CODE_REVIEW_SUMMARY.md` for all 55 review points organized by priority (Critical/High/Medium).

## REST API Endpoints

Current endpoint structure (note: intentionally non-RESTful):

| Method | Endpoint | Purpose | Issue |
|--------|----------|---------|-------|
| GET | `/get/product/by/{id}` | Get product by ID | URL contains verb |
| POST | `/create/product` | Create product | Should use base path |
| POST | `/delete/product/{id}` | Delete product | Should be DELETE method |
| POST | `/update/product` | Update product | Should be PATCH/PUT |
| POST | `/product/list` | List products (paginated) | Should be GET with query params |
| GET | `/product/category/list` | List unique categories | Works correctly |

## JPA Entity: Product

```java
@Entity
@Getter
@Setter  // Intentional issue: breaks immutability
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String category;  // No constraints (intentional)
    private String name;      // No constraints (intentional)
}
```

**Key Issues**:
- `@Setter` allows unrestricted mutation
- No `@Column(nullable = false)` constraints
- `GenerationType.AUTO` instead of explicit strategy
- Duplicate manual getters despite `@Getter`

## Working with This Codebase

### When Adding Features
1. **DO NOT** automatically fix existing issues unless explicitly requested
2. Follow the intentional anti-pattern style for consistency
3. Document new code with review comments using the standard format
4. Add corresponding entries to `CODE_REVIEW_SUMMARY.md`

### When Refactoring
1. Reference specific review numbers from `CODE_REVIEW_SUMMARY.md`
2. Apply improvements according to the documented 개선안 (improvement plan)
3. Verify changes match the 전/후 비교 (before/after comparison)
4. Update review status in the summary document

### Testing Strategy
- H2 in-memory database resets on each restart
- `spring.jpa.hibernate.ddl-auto=update` creates schema automatically
- `spring.jpa.show-sql=true` logs all SQL queries
- No test data initialization (`spring.sql.init.mode=never`)

### Common Debugging
```bash
# Check SQL queries in console
./gradlew bootRun
# Look for Hibernate SQL output with format_sql=true

# Test API endpoints
curl http://localhost:8080/get/product/by/1
curl -X POST http://localhost:8080/create/product \
  -H "Content-Type: application/json" \
  -d '{"category":"전자제품","name":"노트북"}'
```

## Performance Considerations

**Current Performance Issues** (intentional):

1. **N+1 Queries**: Potential lazy loading issues with entity exposure
2. **Redundant Queries**: Service layer queries before delete (2 queries instead of 1)
3. **Unnecessary SELECT**: `save()` call after entity modification triggers extra SELECT
4. **Missing Indexes**: No indexes on `category` column despite frequent queries
5. **No Read-Only Optimization**: Missing `@Transactional(readOnly = true)`

**Typical Improvements** (see CODE_REVIEW_SUMMARY.md):
- Use JPA Dirty Checking instead of explicit `save()` → 30-50% faster updates
- Replace `getById() + delete()` with `deleteById()` → 50% fewer queries
- Add `@Transactional(readOnly = true)` → 5-10% faster reads

## Security Notes

**Current Vulnerabilities** (intentional):

1. No input validation (`@Valid` missing)
2. Entity exposure could leak sensitive fields if added later
3. No pagination limits (size parameter unbounded)
4. Error messages don't include request context
5. All exceptions return 500 instead of appropriate status codes

## Configuration Files

**application.properties** (src/main/resources):
- H2 database in MySQL compatibility mode
- JPA DDL auto-update enabled
- SQL logging and formatting enabled
- H2 console accessible at `/h2-console`

**build.gradle**:
- Spring Boot 3.5.7
- Java 17 toolchain
- Lombok configuration
- JUnit Platform for tests

## Review Assignment Context

This codebase simulates a **junior developer's submission** for code review. The assignment goals:

1. **Problem Identification**: Find issues across readability, security, performance, design
2. **Root Cause Analysis**: Explain why problems exist
3. **Solution Proposals**: Provide alternatives with tradeoffs and rationale
4. **Verification**: Include before/after metrics, query counts, performance data

**Interview Focus**: Reviewers must defend their review comments in technical interviews, explaining:
- Why each issue matters
- How improvements work
- Tradeoffs of different approaches
- Measurement methodologies
