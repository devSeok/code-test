package com.wjc.codetest.product.model.request;

import lombok.Getter;
import lombok.Setter;

/**
 * [리뷰 41 - DTO Setter 사용]
 * 문제: @Setter 사용으로 역직렬화 후에도 필드 변경 가능
 * 원인: DTO 불변성에 대한 고려 부족
 * 개선안: @Setter 제거하고 생성자 또는 @AllArgsConstructor 사용
 *        - Jackson은 기본 생성자 + setter 또는 생성자로 역직렬화 가능
 *        - DTO는 불변 객체로 설계하는 것이 안전
 *        - @NoArgsConstructor(access = AccessLevel.PRIVATE) + @AllArgsConstructor 조합 권장
 * 검증: @Setter 제거 후에도 JSON 역직렬화 정상 동작 확인
 *
 * [리뷰 42 - 불필요한 생성자]
 * 문제: category만 받는 생성자가 존재하는데 비즈니스적으로 의미 없음
 * 원인: 테스트 목적으로 작성했거나 불필요한 오버로딩
 * 개선안: 사용되지 않는 생성자 삭제
 *        - 상품 생성 시 name은 필수이므로 category만 받는 생성자는 부적절
 * 검증: IDE에서 사용처 검색 (unused 확인)
 *
 * [리뷰 43 - 입력 검증 누락]
 * 문제: 필드에 대한 검증 어노테이션이 없음
 * 원인: Bean Validation 미적용
 * 개선안: jakarta.validation 어노테이션 추가
 *        @NotBlank(message = "카테고리는 필수입니다")
 *        @Size(max = 100, message = "카테고리는 100자 이하여야 합니다")
 *        private String category;
 *        - Controller에서 @Valid와 함께 사용
 * 검증: null/빈 문자열 전송 시 400 Bad Request 반환 확인
 */
@Getter
@Setter
public class CreateProductRequest {
    private String category;
    private String name;

    public CreateProductRequest(String category) {
        this.category = category;
    }

    public CreateProductRequest(String category, String name) {
        this.category = category;
        this.name = name;
    }
}

