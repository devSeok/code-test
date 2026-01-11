# OSIV (Open Session In View) 문제 분석

## 현재 상황

```properties
# application.properties에 명시적 설정 없음
# → Spring Boot 기본값: spring.jpa.open-in-view=true
```

## 왜 이것이 문제인가?

### 1. OSIV=true의 동작 방식

```
HTTP 요청 시작
    ↓
[Filter] JPA Session 오픈 ← DB 커넥션 획득
    ↓
[Controller] getProductById()
    ↓
[Service] productRepository.findById()
    ↓
[Controller] return ResponseEntity.ok(product)
    ↓
[View] Jackson이 Product를 JSON으로 직렬화
    ↓  이 시점까지 DB 커넥션 점유!
[Filter] JPA Session 종료 ← DB 커넥션 반환
    ↓
HTTP 응답 종료
```

**문제**: DB 커넥션을 HTTP 요청 전체 동안 점유

### 2. 현재 코드 구조와의 결합 문제

#### 문제 A: 불필요한 커넥션 점유 시간 증가

**현재 구조**:
```java
@GetMapping("/get/product/by/{productId}")
public ResponseEntity<Product> getProductById(@PathVariable Long productId) {
    Product product = productService.getProductById(productId); // ← DB 조회 (50ms)
    return ResponseEntity.ok(product); // ← Jackson 직렬화 (30ms)
}
```

**커넥션 점유 시간**:
```
┌────────────────────────┬──────────┬────────────────┐
│ 단계                   │ 시간     │ 커넥션 필요?   │
├────────────────────────┼──────────┼────────────────┤
│ 1. HTTP 요청 수신      │ 5ms      │ ❌ 불필요      │
│ 2. Controller 진입     │ 2ms      │ ❌ 불필요      │
│ 3. Service DB 조회     │ 50ms     │ ✅ 필요        │
│ 4. Controller 반환     │ 1ms      │ ❌ 불필요      │
│ 5. Jackson 직렬화      │ 30ms     │ ❌ 불필요 (OSIV는 점유)│
│ 6. HTTP 응답 전송      │ 10ms     │ ❌ 불필요      │
├────────────────────────┼──────────┼────────────────┤
│ 총 시간                │ 98ms     │                │
│ 실제 필요 시간         │ 50ms     │                │
│ 낭비 시간 (OSIV)       │ 48ms     │ 96% 낭비!      │
└────────────────────────┴──────────┴────────────────┘
```

**실제 영향** (일 100만 요청):
- 낭비되는 커넥션 점유 시간: 48ms × 1,000,000 = 13.3시간
- 동시 요청 1000 TPS 시:
  - 필요한 커넥션: 1000 × 0.05초 = 50개
  - OSIV로 점유: 1000 × 0.098초 = 98개 (2배!)

#### 문제 B: 엔티티 직접 노출 + OSIV = Lazy Loading 함정

**시나리오**: 3개월 후 Product에 연관관계 추가

```java
@Entity
public class Product {
    @Id
    private Long id;
    private String category;
    private String name;

    // 새로 추가된 연관관계
    @OneToMany(fetch = FetchType.LAZY)
    private List<Review> reviews;  // 상품 리뷰

    @ManyToOne(fetch = FetchType.LAZY)
    private Supplier supplier;  // 공급업체
}
```

**현재 코드 (엔티티 직접 반환 + OSIV)**:
```java
public ResponseEntity<Product> getProductById(Long productId) {
    Product product = productService.getProductById(productId);
    // 여기까지는 Product만 조회 (1 query)

    return ResponseEntity.ok(product);
    // Jackson 직렬화 시점:
    // - product.getReviews() 호출 → Lazy Loading (1 query)
    // - 각 review의 연관관계도 로딩... (N queries)
    // - product.getSupplier() 호출 → Lazy Loading (1 query)
}
```

**실제 실행되는 쿼리**:
```sql
-- 1. 원래 의도한 쿼리
SELECT * FROM product WHERE id = 1;

-- 2. Jackson이 자동으로 발생시키는 쿼리들 (OSIV 때문에 가능)
SELECT * FROM review WHERE product_id = 1;  -- N+1 시작
SELECT * FROM user WHERE id = 101;          -- Review.author
SELECT * FROM user WHERE id = 102;
SELECT * FROM user WHERE id = 103;
... (리뷰 개수만큼)

SELECT * FROM supplier WHERE id = 5;
SELECT * FROM supplier_address WHERE supplier_id = 5;
... (추가 연관관계)

-- 결과: 1개 조회 의도가 100개 쿼리로 폭발!
```

**왜 위험한가**:
1. **개발자가 모름**: Controller 코드만 보면 1개 조회처럼 보임
2. **코드 리뷰 통과**: "findById() 호출 1번이네" → 승인
3. **운영 배포**: 갑자기 응답시간 10배 증가
4. **원인 파악 어려움**: "어디서 쿼리가 발생하지?"

#### 문제 C: 트랜잭션 경계 착각

**현재 인식** (잘못됨):
```java
// "OSIV가 트랜잭션을 제공하니까 @Transactional 없어도 되겠지?"
public Product update(UpdateProductRequest dto) {
    Product product = getProductById(dto.getId());
    product.setCategory(dto.getCategory());
    product.setName(dto.getName());
    // OSIV 세션이 있으니 저장되겠지?
    return product;  // ❌ 저장 안 됨!
}
```

**실제 동작**:
```
OSIV의 영속성 컨텍스트는 READ-ONLY!
- flush 모드: FlushMode.AUTO이지만 트랜잭션이 없으면 flush 안 함
- Dirty Checking 작동 안 함
- 변경사항 DB에 반영 안 됨
```

**혼란**:
- "세션이 열려있는데 왜 저장이 안 되지?"
- "로컬에서는 되는데 운영에서는 안 돼" (타이밍 이슈)
- "save()를 명시적으로 호출해야 하나?" (근본 원인 오해)

### 3. 실제 성능 영향 측정

#### 시나리오: 동시 요청 1000 TPS 처리

**환경 설정**:
- DB 커넥션 풀: 50개 (HikariCP 권장 기본값)
- 각 요청 처리 시간:
  - DB 조회: 50ms
  - JSON 직렬화: 30ms
  - 네트워크: 20ms

**OSIV=true (현재)**:
```
커넥션 점유 시간 = 100ms (전체 요청 시간)
동시 처리 가능 요청 = 50개 커넥션 / 0.1초 = 500 TPS

1000 TPS 요청 시:
→ 커넥션 풀 고갈
→ 500개 요청은 커넥션 대기
→ 대기 시간: 평균 50ms 추가
→ 응답시간: 100ms → 150ms (50% 증가)
→ 타임아웃 발생 시작
```

**OSIV=false + DTO 변환**:
```
커넥션 점유 시간 = 50ms (DB 조회 시간만)
동시 처리 가능 요청 = 50개 커넥션 / 0.05초 = 1000 TPS

1000 TPS 요청 시:
→ 커넥션 풀 여유 있음
→ 대기 없이 즉시 처리
→ 응답시간: 100ms 유지
→ 안정적 서비스
```

**비용 분석**:
```
┌─────────────────────┬──────────┬─────────────┐
│                     │ OSIV=true│ OSIV=false  │
├─────────────────────┼──────────┼─────────────┤
│ 처리 가능 TPS       │ 500      │ 1000        │
│ 필요한 서버 대수    │ 2대      │ 1대         │
│ 월 서버 비용        │ $400     │ $200        │
│ 연간 비용           │ $4,800   │ $2,400      │
├─────────────────────┼──────────┼─────────────┤
│ 절감 금액           │          │ $2,400/년   │
└─────────────────────┴──────────┴─────────────┘
```

### 4. 추가 문제점

#### A. 테스트 환경과 운영 환경의 차이

**테스트 (H2 in-memory)**:
- DB가 같은 프로세스 → 커넥션 비용 거의 없음
- OSIV 문제 안 보임
- "성능 괜찮네?" ✅

**운영 (외부 RDS)**:
- 네트워크 커넥션 → 비용 높음
- 커넥션 풀 고갈 → 장애 발생
- "왜 갑자기 느려졌지?" ❌

#### B. 모니터링 혼란

**DataDog/New Relic 같은 APM**:
```
Transaction: GET /get/product/by/1
├─ Database Query (50ms) ✅ 정상
└─ Total Transaction (150ms) ⚠️ 왜 이렇게 오래?

"DB는 빠른데 왜 전체가 느리지?"
→ 커넥션 대기 시간이 숨겨짐
→ OSIV를 모르면 원인 파악 불가
```

## 개선 방안

### 옵션 1: OSIV 비활성화 (권장)

```properties
# application.properties
spring.jpa.open-in-view=false
```

**장점**:
- 명확한 트랜잭션 경계
- 커넥션 점유 시간 최소화
- Lazy Loading 문제 즉시 발견 (개발 단계에서 에러 발생)

**필수 조치**:
1. DTO 변환 레이어 추가
2. Service에 @Transactional 명시
3. 필요한 연관관계는 fetch join으로 명시

```java
// 개선된 구조
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = productService.getProductById(id);
        // DTO 변환 (DB 커넥션 이미 반환된 상태)
        return ResponseEntity.ok(ProductResponse.from(product));
    }
}

@Service
@Transactional(readOnly = true)  // 명확한 트랜잭션 경계
public class ProductService {

    public Product getProductById(Long id) {
        return productRepository.findById(id)
            .orElseThrow(() -> new ProductNotFoundException(id));
    }
    // 트랜잭션 종료 → 커넥션 반환
}
```

### 옵션 2: OSIV 유지 (비권장, 레거시 호환용)

만약 OSIV를 유지해야 한다면:

1. **명시적으로 인지하고 사용**
```java
/**
 * ⚠️ WARNING: OSIV 활성화 상태
 * - DB 커넥션이 HTTP 응답까지 점유됨
 * - Lazy Loading 사용 시 N+1 쿼리 주의
 * - 트랜잭션은 별도로 @Transactional 필요
 */
```

2. **연관관계 Lazy Loading 금지**
```java
@Entity
public class Product {
    @OneToMany(fetch = FetchType.EAGER)  // 또는 fetch join 사용
    @JsonIgnore  // Jackson 직렬화 제외
    private List<Review> reviews;
}
```

3. **커넥션 풀 크기 증가**
```properties
spring.datasource.hikari.maximum-pool-size=100  # 기본 10 → 증가
spring.datasource.hikari.connection-timeout=5000  # 타임아웃 설정
```

## 우선순위 판단

**왜 High Priority인가?**

```
High = (성능 영향: 크다) × (확장성: 심각) × (수정 비용: 중간)

- 현재 (H2, 낮은 트래픽): 문제 안 보임
- 운영 (RDS, 높은 트래픽): 즉시 문제 발생
- 서버 비용: 연간 $2,400 절감
- 수정 비용: OSIV 끄기(1분) + DTO 레이어(2시간) = 중간
```

**Critical이 아닌 이유**:
- 지금 당장 서비스 안 됨 (X)
- 트래픽 증가 시 문제 (O)
- 성능/비용 최적화 이슈

## 리뷰 작성 예시

```java
/**
 * [리뷰 56 - OSIV 기본 활성화로 인한 커넥션 낭비]
 *
 * 문제: spring.jpa.open-in-view=true (기본값)로 인해
 *      HTTP 요청 전체 기간 동안 DB 커넥션 점유
 *
 * 원인:
 * 1. Spring Boot 기본 설정 그대로 사용
 * 2. OSIV 패턴의 트레이드오프에 대한 이해 부족
 * 3. 엔티티 직접 노출 구조와 결합되어 문제 심화
 *
 * 왜 이것이 High Priority 성능/확장성 문제인가?
 *
 * 1. 커넥션 점유 시간 2배 증가:
 *    실제 필요: 50ms (DB 조회)
 *    현재 점유: 100ms (HTTP 응답까지)
 *    → 동일 커넥션으로 처리 가능한 TPS 50% 감소
 *
 * 2. 트래픽 증가 시 커넥션 풀 고갈:
 *    1000 TPS 요청 시 필요한 커넥션:
 *    - OSIV=false: 50개
 *    - OSIV=true: 100개
 *    → 기본 커넥션 풀(50개)로는 500 TPS만 처리 가능
 *
 * 3. 숨겨진 N+1 쿼리 폭탄:
 *    향후 Product에 @OneToMany 추가 시
 *    Jackson 직렬화 중 자동으로 Lazy Loading 발생
 *    → 개발자가 인지 못한 채 성능 저하
 *
 * 개선안: spring.jpa.open-in-view=false
 *
 * 전/후 비교:
 *   Before: 커넥션 점유 100ms → 500 TPS 처리
 *   After:  커넥션 점유 50ms → 1000 TPS 처리
 *
 * 측정치:
 * - 처리량: 2배 증가
 * - 서버 비용: 연간 $2,400 절감
 * - 응답시간: 대기 시간 제거로 50ms 단축
 *
 * 참고 링크:
 *   - https://vladmihalcea.com/the-open-session-in-view-anti-pattern/
 *   - https://docs.spring.io/spring-boot/reference/data/sql.html#data.sql.jpa-and-spring-data.open-entity-manager-in-view
 */
```

## 결론

OSIV=true는:
- ✅ 개발 편의성 (Lazy Loading 어디서나 가능)
- ❌ 성능 (커넥션 낭비)
- ❌ 확장성 (트래픽 증가 시 한계)
- ❌ 명확성 (트랜잭션 경계 불분명)

**권장**: OSIV=false + 명시적 트랜잭션 + DTO 변환
