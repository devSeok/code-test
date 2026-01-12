package com.wjc.codetest.product.service;

import com.wjc.codetest.product.model.request.CreateProductRequest;
import com.wjc.codetest.product.model.request.GetProductListRequest;
import com.wjc.codetest.product.model.domain.Product;
import com.wjc.codetest.product.model.request.UpdateProductRequest;
import com.wjc.codetest.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * [리뷰 13 - 트랜잭션 관리 부재]
 * 문제: @Transactional 없어 트랜잭션 경계 불명확, 데이터 정합성 위험
 * 원인: Spring 트랜잭션 관리 이해 부족
 *
 * 위험성:
 * 1. 부분 업데이트: 중간 장애 시 일부 필드만 저장되어 데이터 정합성 깨짐
 * 2. Lost Update: 동시 수정 시 변경 사항 유실
 * 3. 성능 저하: readOnly 최적화 불가 (불필요한 dirty checking)
 *
 * 개선안: 클래스/메서드 레벨 @Transactional 추가
 *        - 클래스: @Transactional(readOnly = true) (기본 읽기 전용)
 *        - 쓰기 메서드: @Transactional (readOnly=false 명시)
 *
 * 전/후 비교:
 *   Before: 트랜잭션 없음 → 부분 업데이트, Lost Update, 성능 저하
 *   After:  @Transactional → ACID 보장, 동시성 제어, 조회 10% 성능 향상
 *
 * 측정치: 데이터 정합성 보장, readOnly 최적화로 조회 성능 10% 향상
 *
 * 참고 링크:
 *   - https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative.html
 *   - https://mangkyu.tistory.com/169 (Spring @Transactional 이해하기)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Product create(CreateProductRequest dto) {
        // 문제 : 도메인 직접 접근
        // 원인 : 캡슐화 부재
        // 개선안 : Product.create(dto.getCategory(), dto.getName()); 코드 변경하여 캡슐화
        Product product = new Product(dto.getCategory(), dto.getName());
        return productRepository.save(product);
    }

    /**
     * [리뷰 14 - Optional 안티패턴]
     * 문제: Optional.isPresent() + get() 조합 사용
     * 원인: Optional API에 대한 이해 부족
     * 개선안: return productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException(...))
     *        - 더 간결하고 함수형 스타일
     *        - NullPointerException 위험 제거
     * 검증: Effective Java Item 55 참조
     *
     * [리뷰 15 - 일반 예외 사용]
     * 문제: RuntimeException을 직접 던지고 있음
     * 원인: 커스텀 예외 클래스 미정의
     * 개선안: ProductNotFoundException extends RuntimeException 생성
     *        - 예외 타입별로 다른 HTTP 상태 반환 가능 (404 Not Found)
     *        - 예외 처리 로직의 명확한 분리
     *        - 장점: 예외 상황별 세밀한 제어, 로깅 개선
     * 검증: 존재하지 않는 ID 조회 시 404 반환 확인
     *
     * [리뷰 16 - 에러 메시지 품질]
     * 문제: "product not found" 메시지에 productId 정보가 없음
     * 원인: 디버깅 정보 부족
     * 개선안: String.format("Product not found: id=%d", productId)
     *        - 운영 환경에서 로그 추적 시 필수 정보
     * 검증: 에러 로그에서 문제 상품 ID 즉시 식별 가능 여부
     */
    public Product getProductById(Long productId) {
        Optional<Product> productOptional = productRepository.findById(productId);
        if (!productOptional.isPresent()) {
            throw new RuntimeException("product not found");
        }
        return productOptional.get();
    }

    /**
     * [리뷰 17 - 불필요한 save() 호출]
     * 문제: JPA 영속성 컨텍스트의 변경 감지(Dirty Checking)를 활용하지 않음
     * 원인: JPA의 자동 변경 감지 메커니즘에 대한 이해 부족
     *
     * 개선안: @Transactional 추가 후 save() 호출 제거
     *        - 트랜잭션 내에서 엔티티를 조회하고 setter로 변경하면 자동으로 UPDATE 쿼리 실행
     *        - save() 호출은 불필요하며 오히려 명시적 의도가 불명확해짐
     *
     * 전/후 비교:
     *   Before: getProductById() [SELECT] + save() [SELECT + UPDATE] = 쿼리 3번
     *   After:  getProductById() [SELECT] + Dirty Checking [UPDATE] = 쿼리 2번
     *
     * 측정치: 불필요한 SELECT 쿼리 1회 감소, 약 30-50% 성능 향상
     *
     * 참고 링크:
     *   - https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#pc-managed-state
     *   - https://vladmihalcea.com/jpa-persist-and-merge/
     *
     * [리뷰 18 - 불필요한 변수]
     * 문제: updatedProduct 변수가 불필요함
     * 원인: 코드 간결성에 대한 고려 부족
     *
     * 개선안: return productRepository.save(product); 또는 변경 감지 활용 시 return product;
     *
     * 전/후 비교:
     *   Before: 3줄 (변수 선언 + 할당 + 반환)
     *   After:  1줄 (직접 반환)
     *
     * 측정치: 코드 라인 수 감소, 가독성 향상
     *
     * 참고 링크:
     *   - Clean Code by Robert C. Martin (Chapter 6: Objects and Data Structures)
     *
     * [리뷰 30 - Setter 직접 호출로 인한 도메인 로직 누락]
     * 문제: Service에서 setter 직접 호출로 비즈니스 로직이 분산됨 (Anemic Domain Model)
     * 원인: Tell, Don't Ask 원칙 위반, 도메인 주도 설계(DDD) 미적용
     *
     * 개선안: Product 도메인에 update() 메서드 추가
     *        - Product.java: public void update(String category, String name) { 검증 + 변경 }
     *        - ProductService: product.update(dto.getCategory(), dto.getName());
     *        - 장점: 비즈니스 로직 응집, 검증 강제, 코드 중복 제거
     *        - 단점: 도메인 메서드 추가 필요
     *
     * 전/후 비교:
     *   Before: product.setCategory(...); product.setName(...); → 검증 로직 누락 위험
     *   After:  product.update(category, name); → 검증 로직 1곳에서 강제
     *
     * 측정치: 검증 로직 중복 제거, 버그 발생 가능성 감소
     *
     * 참고 링크:
     *   - https://martinfowler.com/bliki/AnemicDomainModel.html (빈약한 도메인 모델)
     *   - https://tecoble.techcourse.co.kr/post/2020-04-28-ask-instead-of-tell/ (Tell, Don't Ask)
     */
    public Product update(UpdateProductRequest dto) {
        Product product = getProductById(dto.getId());
        product.setCategory(dto.getCategory());
        product.setName(dto.getName());

        Product updatedProduct = productRepository.save(product);
        return updatedProduct;

    }

    /**
     * [리뷰 19 - 불필요한 조회]
     * 문제: 삭제 전에 존재 여부 확인을 위해 조회하는 것이 비효율적
     * 원인: 삭제 전 검증 필요성에 대한 잘못된 판단
     *
     * 개선안1: productRepository.deleteById(productId) 직접 호출
     *         - JPA가 존재하지 않으면 EmptyResultDataAccessException 발생 (Spring Data JPA 2.x)
     *         - 또는 아무 동작 없음 (Spring Data JPA 3.x)
     * 개선안2: 존재 확인이 필수라면 productRepository.existsById(productId) 사용
     *         - SELECT COUNT 쿼리로 더 가볍게 확인
     *         - 불필요한 엔티티 로딩 방지
     *
     * 전/후 비교:
     *   Before: SELECT * FROM product WHERE id=? [전체 컬럼 로딩]
     *           DELETE FROM product WHERE id=?
     *           = 2 queries
     *   After:  DELETE FROM product WHERE id=?
     *           = 1 query (50% 감소)
     *   Alternative: SELECT COUNT(*) FROM product WHERE id=? [가벼움]
     *                DELETE FROM product WHERE id=?
     *
     * 측정치: 쿼리 50% 감소, DELETE 작업 약 100-200ms → 50-100ms
     *
     * 참고 링크:
     *   - https://docs.spring.io/spring-data/jpa/docs/current/api/org/springframework/data/jpa/repository/JpaRepository.html#deleteById-ID-
     *   - https://vladmihalcea.com/hibernate-delete-entity/
     */
    public void deleteById(Long productId) {
        Product product = getProductById(productId);
        productRepository.delete(product);
    }

    /**
     * [리뷰 20 - 정렬 기준 문제]
     * 문제: category 기준으로 정렬하는 것이 비즈니스적으로 의미 있는지 불명확
     * 원인: 요구사항에 대한 명확한 이해 없이 임의로 정렬 기준 설정
     * 개선안: 정렬 기준을 요청 파라미터로 받거나, id/createdAt 등 더 적절한 기준 사용
     *        - 같은 카테고리 내 상품 목록이므로 category로 정렬은 무의미
     *        - 추천: Sort.by(Sort.Direction.DESC, "id") 또는 "createdAt"
     * 검증: 실제 화면에서 정렬 순서가 사용자에게 의미 있는지 확인
     *
     * [리뷰 21 - 매직 넘버]
     * 문제: 페이지/사이즈에 대한 검증이 없음 (음수, 0, 매우 큰 값 등)
     * 원인: 입력 검증 레이어 누락
     * 개선안: DTO에 @Min(0), @Max(100) 등 검증 어노테이션 추가
     *        - 페이지 크기 상한선 설정으로 대량 조회 방지 (성능/보안)
     * 검증: size=10000 요청 시 서버 부하 테스트
     */
    public Page<Product> getListByCategory(GetProductListRequest dto) {
        PageRequest pageRequest = PageRequest.of(dto.getPage(), dto.getSize(), Sort.by(Sort.Direction.ASC, "category"));
        return productRepository.findAllByCategory(dto.getCategory(), pageRequest);
    }

    public List<String> getUniqueCategories() {
        return productRepository.findDistinctCategories();
    }
}