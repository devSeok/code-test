package com.wjc.codetest.product.model.response;

import com.wjc.codetest.product.model.domain.Product;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * [리뷰 51 - 개인정보 포함]
 * 문제: JavaDoc에 개발자 이메일 주소가 노출됨
 * 원인: IDE 자동 생성 템플릿을 그대로 사용
 * 개선안: @author와 이메일 정보 제거
 *        - 소스 코드 공개 시 개인정보 노출 위험
 *        - Git 커밋 이력으로 작성자 추적 가능
 * 검증: 공개 저장소에 올리기 전 민감정보 확인
 *
 * [리뷰 52 - since 날짜 오류]
 * 문제: @since 2025-10-27인데 현재 2026년 1월
 * 원인: 미래 날짜 또는 오타
 * 개선안: 정확한 날짜로 수정 또는 @since 제거
 *        - 유지보수 혼란 방지
 * 검증: 실제 프로젝트 시작일 확인
 *
 * [리뷰 53 - 엔티티 직접 노출]
 * 문제: List<Product> 엔티티를 직접 반환
 * 원인: DTO 변환 레이어 누락
 * 개선안: List<ProductResponse>로 변경
 *        - Product 엔티티 변경 시 API 응답 영향 없도록 격리
 *        - 향후 엔티티에 민감정보 추가 시 자동 노출 방지
 *        - JPA lazy loading 시 N+1 문제나 프록시 직렬화 이슈 방지
 * 검증: Product에 password 필드 추가 시 자동 노출 여부
 */
/**
 * <p>
 *
 * </p>
 *
 * @author : 변영우 byw1666@wjcompass.com
 * @since : 2025-10-27
 */
@Getter
@Setter
public class ProductListResponse {
    /**
     * [리뷰 54 - 생성자 파라미터명 불일치]
     * 문제: 생성자 파라미터가 content, number인데 필드명은 products, page
     * 원인: Spring Data Page 객체의 메서드명을 그대로 사용
     * 개선안: 파라미터명을 필드명과 일치시키기
     *        public ProductListResponse(List<Product> products, int totalPages, long totalElements, int page)
     *        - 코드 가독성 향상, 의도 명확화
     * 검증: 파라미터명 변경 후 컴파일 확인
     *
     * [리뷰 55 - Setter 제거 권장]
     * 문제: Response DTO에 @Setter가 있어 반환 후에도 변경 가능
     * 원인: DTO 불변성에 대한 고려 부족
     * 개선안: @Setter 제거
     *        - Response는 생성 후 변경할 이유가 없음
     *        - 불변 객체로 설계하여 안전성 향상
     * 검증: Jackson 직렬화는 getter만 있으면 동작
     */
    private List<Product> products;
    private int totalPages;
    private long totalElements;
    private int page;

    public ProductListResponse(List<Product> content, int totalPages, long totalElements, int number) {
        this.products = content;
        this.totalPages = totalPages;
        this.totalElements = totalElements;
        this.page = number;
    }
}
