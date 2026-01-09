package com.wjc.codetest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * [리뷰 34 - 과도하게 광범위한 예외 처리]
 * 문제: 모든 RuntimeException을 500 에러로 처리
 * 원인: 예외 타입별 분류 없이 일괄 처리
 * 개선안: 예외 타입별로 핸들러 분리
 *        1. ProductNotFoundException → 404 Not Found
 *        2. IllegalArgumentException → 400 Bad Request
 *        3. 기타 RuntimeException → 500 Internal Server Error
 *        - 클라이언트가 에러 원인을 정확히 파악 가능
 *        - RESTful API 표준 준수
 * 검증: 존재하지 않는 상품 조회 시 404 반환 확인
 *
 * [리뷰 35 - 에러 응답 Body 없음]
 * 문제: ResponseEntity.build()로 빈 body 반환
 * 원인: 에러 메시지를 클라이언트에 전달할 필요성 인식 부족
 * 개선안: ErrorResponse DTO를 생성하여 구조화된 에러 정보 반환
 *        {
 *          "timestamp": "2024-01-09T10:30:00",
 *          "status": 500,
 *          "error": "Internal Server Error",
 *          "message": "상품을 찾을 수 없습니다",
 *          "path": "/api/v1/products/999"
 *        }
 *        - 클라이언트 디버깅 용이, 사용자 친화적 에러 메시지 제공
 * 검증: Postman에서 에러 응답 body 확인
 */
@Slf4j
@ControllerAdvice(value = {"com.wjc.codetest.product.controller"})
public class GlobalExceptionHandler {

    /**
     * [리뷰 36 - @ResponseBody 중복]
     * 문제: @ControllerAdvice는 이미 @ResponseBody 의미 포함
     * 원인: Spring MVC 어노테이션에 대한 이해 부족
     * 개선안: @ResponseBody 제거 (ResponseEntity 반환 시 불필요)
     *        - @RestControllerAdvice 사용 시 자동 포함
     *        - 또는 @ControllerAdvice + ResponseEntity 조합 사용
     * 검증: @ResponseBody 제거 후에도 JSON 응답 확인
     *
     * [리뷰 37 - @ResponseStatus와 ResponseEntity 중복]
     * 문제: @ResponseStatus와 ResponseEntity.status() 동시 사용
     * 원인: 상태 코드 설정 방식에 대한 혼란
     * 개선안: 둘 중 하나만 사용
     *        - ResponseEntity 사용 시 @ResponseStatus 불필요 (ResponseEntity가 우선)
     *        - 일관성 있게 ResponseEntity만 사용 권장
     * 검증: @ResponseStatus 제거 후 여전히 500 반환 확인
     *
     * [리뷰 38 - 로깅 보안 이슈]
     * 문제: e.getMessage()를 직접 로깅하여 민감정보 노출 위험
     * 원인: 로깅 보안에 대한 고려 부족
     * 개선안: log.error("Runtime exception occurred", e) 사용
     *        - 전체 스택 트레이스 기록으로 디버깅 용이
     *        - 단, 민감정보가 예외 메시지에 포함되지 않도록 주의
     * 검증: 로그 파일에서 스택 트레이스 확인
     *
     * [리뷰 39 - 메서드명 오타]
     * 문제: runTimeException → runtimeException (Java 관례)
     * 원인: 네이밍 컨벤션 미준수
     * 개선안: handleRuntimeException으로 변경
     *        - 핸들러 메서드는 handle* 접두사 사용 권장
     * 검증: 코드 가독성 향상
     *
     * [리뷰 40 - MethodArgumentNotValidException 미처리]
     * 문제: @Valid 검증 실패 시 처리할 핸들러 없음
     * 원인: Bean Validation 예외 처리 누락
     * 개선안: @ExceptionHandler(MethodArgumentNotValidException.class) 추가
     *        - 400 Bad Request + 필드별 에러 메시지 반환
     * 검증: null 값으로 POST 요청 시 적절한 에러 메시지 확인
     */
    @ResponseBody
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(value = HttpStatus.INTERNAL_SERVER_ERROR)
    public ResponseEntity<String> runTimeException(Exception e) {
        log.error("status :: {}, errorType :: {}, errorCause :: {}",
                HttpStatus.INTERNAL_SERVER_ERROR,
                "runtimeException",
                e.getMessage()
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
