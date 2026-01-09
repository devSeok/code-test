package com.wjc.codetest.product.model.request;

import lombok.Getter;
import lombok.Setter;

/**
 * [리뷰 47 - GET 요청에 Body 사용]
 * 문제: 이 DTO가 POST /product/list의 @RequestBody로 사용됨
 * 원인: GET 메서드의 의미론적 사용 방식 오해
 * 개선안: GET 요청으로 변경하고 @RequestParam 또는 @ModelAttribute로 받기
 *        - GET /api/v1/products?category=전자제품&page=0&size=10
 *        - 쿼리 파라미터로 받으면 캐싱, 북마크, 로그 추적 등 이점
 * 검증: HTTP 표준 - GET 요청의 body는 의미가 없음 (RFC 7231)
 *
 * [리뷰 48 - primitive type 사용]
 * 문제: int page, int size는 기본값이 0이므로 null 여부 판단 불가
 * 원인: primitive와 wrapper 타입의 차이에 대한 이해 부족
 * 개선안: Integer page, Integer size로 변경
 *        - null 체크 가능: 클라이언트가 값을 안 보냈는지 확인
 *        - 기본값 설정: @RequestParam(defaultValue = "0")와 함께 사용
 * 검증: 필수가 아닌 파라미터는 wrapper 타입 사용 권장
 *
 * [리뷰 49 - 페이징 검증 누락]
 * 문제: page, size에 대한 검증이 없음 (음수, 0, 매우 큰 값)
 * 원인: 입력 검증 누락
 * 개선안: @Min(0), @Max(100) 등 검증 어노테이션 추가
 *        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다")
 *        private Integer page;
 *        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다")
 *        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다")
 *        private Integer size;
 * 검증: size=10000 요청 시 서버 메모리/성능 이슈 발생 가능
 *
 * [리뷰 50 - 기본값 설정 부재]
 * 문제: 클라이언트가 page, size를 안 보내면 0으로 설정되어 size=0인 경우 에러 발생
 * 원인: 기본값 처리 로직 없음
 * 개선안: 컨트롤러에서 @RequestParam(defaultValue = "0") 또는 DTO 내부에서 처리
 * 검증: page/size 없이 요청 시 동작 확인
 */
@Getter
@Setter
public class GetProductListRequest {
    private String category;
    private int page;
    private int size;
}