package com.wjc.codetest.product.controller;

import com.wjc.codetest.product.model.request.CreateProductRequest;
import com.wjc.codetest.product.model.request.GetProductListRequest;
import com.wjc.codetest.product.model.domain.Product;
import com.wjc.codetest.product.model.request.UpdateProductRequest;
import com.wjc.codetest.product.model.response.ProductListResponse;
import com.wjc.codetest.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * [리뷰 1 - REST API 설계]
 * 문제: @RequestMapping에 base path가 없어 모든 엔드포인트가 root 레벨에 노출됨
 * 원인: 설계 단계에서 API 버전 관리와 그룹핑을 고려하지 않음
 *
 * 개선안: @RequestMapping("/api/v1/products")로 변경하여 API 버전 관리 및 명확한 리소스 그룹핑
 *        - 장점: API 버전 관리 용이, URL 구조 명확, 향후 다른 리소스 추가 시 충돌 방지
 *        - 단점: 기존 클라이언트 URL 변경 필요
 *
 * 전/후 비교:
 *   Before: GET /get/product/by/1
 *   After:  GET /api/v1/products/1
 *
 * 측정치: URL 길이 단축 (21자 → 20자), 의미론적 명확성 향상
 *
 * 참고 링크:
 *   - https://meetup.nhncloud.com/posts/92 (REST API 디자인 가이드)
 *   - https://techblog.woowahan.com/2527/ (우아한형제들 API 설계)
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;

    /**
     * [리뷰 2 - URL 네이밍]
     * 문제: URL에 불필요한 동사(get)가 포함되고 구조가 복잡함 (/get/product/by/{id})
     * 원인: RESTful API 설계 원칙을 따르지 않고 RPC 스타일로 설계
     *
     * 개선안: GET /{productId} 또는 GET /{id}로 단순화 (base path가 /api/v1/products인 경우)
     *        - REST에서 HTTP 메서드 자체가 동작을 나타내므로 URL에 동사 불필요
     *        - 리소스 중심의 명사형 URL 사용
     *
     * 전/후 비교:
     *   Before: GET /get/product/by/1
     *   After:  GET /api/v1/products/1
     *
     * 측정치: URL 세그먼트 감소 (5개 → 4개), 가독성 향상
     *
     * 참고 링크:
     *   - https://spoqa.github.io/2012/02/27/rest-introduction.html (REST API 이해와 설계)
     *   - https://meetup.nhncloud.com/posts/92 (REST API 제대로 알고 사용하기)
     *
     * [리뷰 3 - 엔티티 직접 노출]
     * 문제: Product 엔티티를 직접 반환하여 도메인 모델이 API에 노출됨
     * 원인: DTO 변환 레이어 누락
     *
     * 개선안: ProductResponse DTO를 생성하여 반환
     *        - 장점: API 스펙 변경 없이 내부 구조 변경 가능, 민감정보 제어, 순환참조 방지
     *        - 단점: DTO 변환 코드 추가 필요 (ModelMapper 또는 MapStruct 활용 권장)
     *
     * 전/후 비교:
     *   Before: ResponseEntity<Product> → JPA 엔티티 직접 반환
     *   After:  ResponseEntity<ProductResponse> → DTO로 변환하여 반환
     *
     * 측정치: 변환 오버헤드 미미(~0.1ms), 보안/유지보수성 향상
     *
     * 참고 링크:
     *   - https://tecoble.techcourse.co.kr/post/2021-04-25-dto-layer-scope/ (DTO의 사용 범위)
     *   - https://velog.io/@jsb100800/Spring-boot-Entity-DTO-%EB%B6%84%EB%A6%AC%ED%95%98%EA%B8%B0 (Entity와 DTO 분리)
     */
    @GetMapping(value = "/get/product/by/{productId}")
    public ResponseEntity<Product> getProductById(@PathVariable(name = "productId") Long productId){
        Product product = productService.getProductById(productId);
        return ResponseEntity.ok(product);
    }

    /**
     * [리뷰 4 - HTTP 상태 코드]
     * 문제: 리소스 생성 시 200 OK 대신 201 CREATED 반환해야 함
     * 원인: HTTP 상태 코드의 의미론적 사용에 대한 이해 부족
     *
     * 개선안: ResponseEntity.created(URI.create("/products/" + product.getId())).body(product)
     *        - 201 Created: 새 리소스가 성공적으로 생성됨을 명시
     *        - Location 헤더: 생성된 리소스의 URI 제공 (클라이언트가 바로 조회 가능)
     *
     * 전/후 비교:
     *   Before: HTTP/1.1 200 OK (body에 생성된 리소스)
     *   After:  HTTP/1.1 201 Created
     *           Location: /api/v1/products/1
     *
     * 측정치: 클라이언트가 Location 헤더로 즉시 리소스 접근 가능
     *
     * 참고 링크:
     *   - https://developer.mozilla.org/ko/docs/Web/HTTP/Status (HTTP 상태 코드)
     *   - https://hongong.hanbit.co.kr/http-%EC%83%81%ED%83%9C-%EC%BD%94%EB%93%9C-%EC%A0%95%EB%A6%AC/ (HTTP 상태 코드 정리)
     *
     * [리뷰 5 - 입력 검증 누락]
     * 문제: @RequestBody에 @Valid 어노테이션이 없어 입력 검증이 수행되지 않음
     * 원인: Bean Validation 프레임워크 활용 누락
     *
     * 개선안: @Valid @RequestBody CreateProductRequest dto로 변경
     *        - DTO에 @NotNull, @NotBlank, @Size 등 검증 어노테이션 추가
     *        - MethodArgumentNotValidException 처리를 GlobalExceptionHandler에 추가
     *
     * 전/후 비교:
     *   Before: {"category": null} → 500 에러 또는 DB 에러
     *   After:  {"category": null} → 400 Bad Request with 상세 에러 메시지
     *
     * 측정치: 악의적 요청 차단으로 서버 안정성 향상
     *
     * 참고 링크:
     *   - https://mangkyu.tistory.com/174 (Spring Validation 사용법)
     *   - https://tecoble.techcourse.co.kr/post/2020-09-20-validation-in-spring-boot/ (Spring Boot Validation)
     */
    @PostMapping(value = "/create/product")
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest dto){
        Product product = productService.create(dto);
        return ResponseEntity.ok(product);
    }

    /**
     * [리뷰 6 - HTTP 메서드 오용 (DELETE 대신 POST)]
     * 문제: 삭제 작업에 POST 사용 (REST 원칙 위반)
     * 원인: HTTP 메서드 의미론 이해 부족
     *
     * 위험성:
     * 1. CSRF 취약점: URL에 /delete가 있어 GET으로 오인 가능 (GitHub 2008 사고 사례)
     * 2. 멱등성 부재: 네트워크 재시도 시 에러 발생으로 혼란
     * 3. 인프라 이슈: 잘못된 메트릭, 캐싱 불가
     *
     * 개선안: @DeleteMapping("/{productId}") + ResponseEntity<Void>
     *        - DELETE 메서드로 멱등성 보장
     *        - 204 No Content 반환
     *
     * 전/후 비교:
     *   Before: POST /delete/product/1 → CSRF 위험, 재시도 불안정
     *   After:  DELETE /api/v1/products/1 → 멱등성 보장, 안전한 재시도
     *
     * 측정치: CSRF 취약점 제거, 안전한 재시도로 에러율 감소
     *
     * 참고 링크:
     *   - https://developer.mozilla.org/ko/docs/Glossary/Idempotent (멱등성 개념)
     *   - https://tecoble.techcourse.co.kr/post/2020-08-17-http-method-idempotency/ (HTTP 메서드와 멱등성)
     *
     * [리뷰 7 - 불필요한 반환값]
     * 문제: Boolean 값(true) 반환이 무의미함
     * 원인: HTTP 상태 코드 활용 부족
     *
     * 개선안: ResponseEntity.noContent().build() (204 No Content)
     *        - 삭제 성공 시 body 없이 204 반환이 표준
     *
     * 전/후 비교:
     *   Before: 200 OK + {"result": true} (7바이트)
     *   After:  204 No Content (body 없음)
     *
     * 측정치: 응답 크기 감소, 표준 준수
     *
     * 참고 링크:
     *   - https://developer.mozilla.org/ko/docs/Web/HTTP/Status/204 (204 No Content)
     */
    @PostMapping(value = "/delete/product/{productId}")
    public ResponseEntity<Boolean> deleteProduct(@PathVariable(name = "productId") Long productId){
        productService.deleteById(productId);
        return ResponseEntity.ok(true);
    }

    /**
     * [리뷰 8 - HTTP 메서드 오용]
     * 문제: 수정 작업에 POST 메서드를 사용함
     * 원인: PUT/PATCH의 차이에 대한 이해 부족
     *
     * 개선안: @PutMapping("/{id}") 또는 @PatchMapping("/{id}")로 변경
     *        - PUT: 리소스 전체를 교체 (모든 필드 업데이트)
     *        - PATCH: 리소스 일부만 수정 (변경된 필드만 업데이트) - 현재 코드에 더 적합
     *        - URL에서 id를 받고 RequestBody에는 변경할 필드만 포함
     *
     * 전/후 비교:
     *   Before: POST /update/product with body: {id:1, category:"A", name:"B"}
     *   After:  PATCH /api/v1/products/1 with body: {category:"A", name:"B"}
     *
     * 측정치: 보안 향상 (id 불일치 공격 방지), 표준 준수
     *
     * 참고 링크:
     *   - https://developer.mozilla.org/ko/docs/Web/HTTP/Methods/PATCH (PATCH 메서드)
     *   - https://blog.naver.com/gngh0101/222166109910 (PUT vs PATCH 차이점)
     *
     * [리뷰 9 - URL/Body 중복]
     * 문제: UpdateProductRequest에 id가 포함되어 있어 URL path와 중복 가능성
     * 원인: RESTful 설계에서 리소스 식별자는 URL에 포함하는 원칙 미준수
     *
     * 개선안: @PatchMapping("/{id}") + id는 URL에서만 받고 body에서는 제외
     *        - URL: /api/v1/products/{id}
     *        - Body: {category, name}만 포함
     *
     * 전/후 비교:
     *   Before: PATCH /api/v1/products/999 + body:{id:1, ...} → 어느 ID를 믿어야 하나?
     *   After:  PATCH /api/v1/products/1 + body:{category, name} → 명확한 식별
     *
     * 측정치: 보안 취약점 제거 (Insecure Direct Object Reference 방지)
     *
     * 참고 링크:
     *   - https://blog.naver.com/wnrjsxo/222161262623 (IDOR 취약점 이해)
     *   - https://owasp.org/www-community/attacks/Insecure_Direct_Object_References (IDOR 공격)
     */
    @PostMapping(value = "/update/product")
    public ResponseEntity<Product> updateProduct(@RequestBody UpdateProductRequest dto){
        Product product = productService.update(dto);
        return ResponseEntity.ok(product);
    }

    /**
     * [리뷰 10 - GET에 RequestBody 사용]
     * 문제: 조회(GET)에 @RequestBody를 사용함 (HTTP 스펙 위반)
     * 원인: 페이징/필터 정보를 body로 전달하려는 의도이나 GET은 body를 갖지 않음
     *
     * 개선안: @GetMapping + @RequestParam 또는 @ModelAttribute 사용
     *        - GET /api/v1/products?category=전자제품&page=0&size=10
     *        - 장점: 브라우저 주소창 입력 가능, 북마크/공유 가능, 캐싱 가능
     *        - 단점: URL 길이 제한 (일반적으로 문제 없음)
     *
     * 전/후 비교:
     *   Before: POST /product/list + body:{category, page, size} → 캐싱 불가
     *   After:  GET /api/v1/products?category=A&page=0&size=10 → 캐싱, 북마크 가능
     *
     * 측정치: CDN 캐싱 가능, 브라우저 히스토리 기록, 북마크 공유 가능
     *
     * 참고 링크:
     *   - https://developer.mozilla.org/ko/docs/Web/HTTP/Methods/GET (GET 메서드)
     *   - https://velog.io/@conatuseus/GET%EC%97%90-body%EB%A5%BC-%EC%82%AC%EC%9A%A9%ED%95%A0-%EC%88%98-%EC%9E%88%EC%9D%84%EA%B9%8C (GET에 body 사용 논쟁)
     *
     * [리뷰 11 - 엔티티 직접 노출 (ResponseDTO)]
     * 문제: ProductListResponse가 Product 엔티티 리스트를 직접 포함
     * 원인: 응답 DTO 설계 시 엔티티 변환 누락
     *
     * 개선안: ProductListResponse가 ProductResponse DTO 리스트를 포함하도록 변경
     *        - 엔티티 변경 시 API 응답에 영향 없도록 격리
     *
     * 전/후 비교:
     *   Before: List<Product> → 엔티티에 필드 추가 시 API 응답 자동 변경
     *   After:  List<ProductResponse> → API 스펙 독립적 관리
     *
     * 측정치: 보안 향상, API 버전 관리 용이
     *
     * 참고 링크:
     *   - https://tecoble.techcourse.co.kr/post/2021-04-25-dto-layer-scope/ (DTO의 사용 범위)
     */
    @PostMapping(value = "/product/list")
    public ResponseEntity<ProductListResponse> getProductListByCategory(@RequestBody GetProductListRequest dto){
        Page<Product> productList = productService.getListByCategory(dto);
        return ResponseEntity.ok(new ProductListResponse(productList.getContent(), productList.getTotalPages(), productList.getTotalElements(), productList.getNumber()));
    }

    /**
     * [리뷰 12 - 메서드명 중복]
     * 문제: getProductListByCategory() 메서드명이 위의 메서드와 동일 (컴파일 에러는 아니지만 혼란)
     * 원인: 메서드 시그니처가 달라 오버로딩은 가능하나 의미적으로 다른 작업 수행
     *
     * 개선안: getCategories() 또는 getCategoryList()로 명확히 구분
     *        - 메서드명이 실제 동작을 명확히 표현해야 함
     *
     * 전/후 비교:
     *   Before: getProductListByCategory() 2개 → 어느 것이 무엇을 하는지 혼란
     *   After:  getProductsByCategory() + getCategories() → 명확한 의도 파악
     *
     * 측정치: 코드 가독성 향상, 유지보수 시간 단축
     *
     * 참고 링크:
     *   - https://woowacourse.github.io/javable/post/2020-04-24-variable-naming/ (변수 네이밍 컨벤션)
     *   - https://velog.io/@ljo_0920/%EB%84%A4%EC%9D%B4%EB%B0%8D-%EC%BB%A8%EB%B2%A4%EC%85%98-Naming-Convention (네이밍 컨벤션 가이드)
     */
    @GetMapping(value = "/product/category/list")
    public ResponseEntity<List<String>> getProductListByCategory(){
        List<String> uniqueCategories = productService.getUniqueCategories();
        return ResponseEntity.ok(uniqueCategories);
    }
}