package com.wjc.codetest.product.repository;

import com.wjc.codetest.product.model.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * [리뷰 29 - @Repository 불필요]
 * 문제: JpaRepository를 상속하는 인터페이스에 @Repository 어노테이션 중복
 * 원인: Spring Data JPA 동작 방식에 대한 이해 부족
 * 개선안: @Repository 제거
 *        - Spring Data JPA가 자동으로 빈 등록 및 예외 변환 처리
 *        - @Repository는 구체 클래스에만 필요 (예: 직접 구현한 DAO 클래스)
 * 검증: @Repository 제거 후에도 정상 동작 확인
 *
 * 참조 링크 :
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * [리뷰 30 - 매개변수명 오류]
     * 문제: 메서드 파라미터명이 'name'인데 실제로는 category 값을 받음
     * 원인: 네이밍 실수
     * 개선안: String category로 변경
     *        - 매개변수명이 의도를 명확히 표현해야 함
     *        - 혼란을 야기하는 잘못된 네이밍
     * 검증: IDE에서 메서드 호출 시 힌트 확인
     *
     * [리뷰 31 - 메서드명 검토]
     * 문제: findAllByCategory라는 메서드명이 모든 상품을 찾는 것처럼 보임
     * 원인: 메서드명 컨벤션에 대한 이해 부족
     * 개선안: findByCategory 또는 findByCategoryOrderByIdDesc 권장
     *        - "All"은 실제로 전체가 아니라 필터된 결과이므로 불필요
     *        - Spring Data JPA 메서드명 규칙 준수
     * 검증: Spring Data JPA Query Creation 문서 참조
     */
    Page<Product> findAllByCategory(String name, Pageable pageable);

    /**
     * [리뷰 32 - 쿼리 최적화 검토]
     * 문제: DISTINCT 사용이 적절하나, 대량 데이터에서는 성능 이슈 가능
     * 원인: 단순 JPQL 작성
     * 개선안: 현재 코드는 적절하나, 향후 최적화 고려사항 제시
     *        1. 캐싱: @Cacheable("categories") 추가 고려 (변경 빈도 낮은 경우)
     *        2. 인덱스: category 컬럼에 인덱스 추가 (많은 데이터 시)
     *        3. Native Query: SELECT DISTINCT category FROM product (성능 중요 시)
     * 검증: 상품 1만건 이상 시 쿼리 실행 시간 측정
     *
     * [리뷰 33 - SQL Injection 안전성]
     * 문제: 없음 - JPQL은 파라미터 바인딩으로 안전
     * 검증: JPQL은 PreparedStatement 사용으로 SQL Injection 방어
     */
    @Query("SELECT DISTINCT p.category FROM Product p")
    List<String> findDistinctCategories();
}
