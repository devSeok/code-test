package com.wjc.codetest.product.model.request;

import lombok.Getter;
import lombok.Setter;

/**
 * [리뷰 44 - ID를 Body에 포함]
 * 문제: 리소스 식별자(id)를 Request Body에 포함하는 것은 RESTful 원칙 위반
 * 원인: REST API 설계 원칙에 대한 이해 부족
 * 개선안: id를 URL path parameter로 받고 body에서는 제외
 *        - URL: PATCH /api/v1/products/{id}
 *        - Body: {category, name}만 포함
 *        - 보안: URL의 id와 body의 id가 다를 경우 혼란 및 보안 이슈
 * 검증: URL path의 id와 body의 id가 다를 때 어느 것을 사용할지 불명확
 *
 * [리뷰 45 - 과도한 생성자 오버로딩]
 * 문제: 3개의 생성자가 각각 다른 조합의 파라미터를 받음
 * 원인: 모든 경우의 수를 지원하려는 과도한 설계
 * 개선안: 필요한 생성자만 유지 (전체 필드를 받는 생성자)
 *        - 부분 업데이트는 null 허용으로 처리
 *        - 또는 Builder 패턴 사용 (@Builder)
 * 검증: 실제 사용되지 않는 생성자 확인
 *
 * [리뷰 46 - 부분 업데이트 의도 불명확]
 * 문제: category만 수정, name만 수정, 둘 다 수정 등의 경우 처리 방법 불명확
 * 원인: PATCH 의미론에 대한 이해 부족
 * 개선안: null을 "변경하지 않음"으로 해석하는 로직 명시
 *        - 서비스 레이어에서 null이 아닌 필드만 업데이트
 *        - 또는 별도의 UpdateProductCategoryRequest, UpdateProductNameRequest 분리
 * 검증: name을 null로 보내면 기존 name이 null로 변경되는지, 유지되는지 테스트
 */
@Getter
@Setter
public class UpdateProductRequest {
    private Long id;
    private String category;
    private String name;

    public UpdateProductRequest(Long id) {
        this.id = id;
    }

    public UpdateProductRequest(Long id, String category) {
        this.id = id;
        this.category = category;
    }

    public UpdateProductRequest(Long id, String category, String name) {
        this.id = id;
        this.category = category;
        this.name = name;
    }
}

