package com.wjc.codetest.product.model.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [리뷰 22 - Lombok과 수동 메서드 중복]
 * 문제: @Getter 어노테이션을 사용하면서 getCategory(), getName() 메서드를 중복 정의
 * 원인: Lombok 어노테이션의 동작 방식에 대한 이해 부족
 * 개선안: 수동으로 작성한 getter 메서드 삭제
 *        - @Getter가 컴파일 타임에 자동으로 생성
 *        - 중복 코드 제거로 유지보수성 향상
 * 검증: 컴파일 후 .class 파일 디컴파일로 getter 생성 확인
 *
 * [리뷰 23 - Setter 사용의 위험성]
 * 문제: @Setter 사용으로 모든 필드가 외부에서 변경 가능
 * 원인: 도메인 모델의 캡슐화에 대한 이해 부족
 * 개선안: @Setter 제거 후 필요한 경우에만 비즈니스 메서드 제공
 *        - 예: updateInfo(String category, String name) 메서드로 변경 로직 캡슐화
 *        - 장점: 불변성 증가, 비즈니스 규칙 강제, 변경 이력 추적 용이
 *        - 트레이드오프: Service 레이어에서 setter 대신 메서드 호출
 * 검증: 엔티티 외부에서 setter 호출하는 부분 없는지 확인
 */
@Entity
@Getter
@Setter
public class Product {

    /**
     * [리뷰 24 - GenerationType.AUTO 사용]
     * 문제: GenerationType.AUTO는 데이터베이스마다 다른 전략 사용
     * 원인: ID 생성 전략에 대한 명확한 결정 부재
     * 개선안: GenerationType.IDENTITY 사용 권장 (H2, MySQL 환경)
     *        - AUTO: 데이터베이스 dialect에 따라 자동 선택 (예측 불가)
     *        - IDENTITY: AUTO_INCREMENT 사용 (MySQL, H2 적합)
     *        - SEQUENCE: 시퀀스 사용 (PostgreSQL, Oracle 적합)
     *        - TABLE: 별도 테이블로 ID 관리 (성능 이슈, 비권장)
     * 검증: 로그에서 실제 사용되는 ID 생성 전략 확인
     */
    @Id
    @Column(name = "product_id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    /**
     * [리뷰 25 - @Column name 불필요]
     * 문제: 필드명과 컬럼명이 동일한 경우 @Column(name=...) 불필요
     * 원인: JPA naming strategy에 대한 이해 부족
     * 개선안: @Column 제거 또는 name 속성 제거
     *        - Spring Boot 기본 전략: 카멜케이스 → 스네이크케이스 (category → category)
     *        - 동일한 경우 명시할 필요 없음 (코드 간결성)
     * 검증: @Column 제거 후에도 동일한 컬럼명 매핑 확인
     *
     * [리뷰 26 - 필드 검증 누락]
     * 문제: category와 name에 대한 제약 조건이 없음
     * 원인: 도메인 검증 로직 누락
     * 개선안: @Column(nullable = false, length = 100) 추가
     *        - nullable: DB NOT NULL 제약
     *        - length: VARCHAR 길이 명시
     *        - 추가로 @NotBlank (Bean Validation) 고려
     * 검증: null 값으로 저장 시도 시 예외 발생 확인
     */
    @Column(name = "category")
    private String category;

    @Column(name = "name")
    private String name;

    /**
     * [리뷰 27 - JPA 표준 준수]
     * 문제: 없음 - protected 기본 생성자는 JPA 요구사항으로 올바름
     * 검증: JPA 스펙은 매개변수 없는 생성자 필수 (protected 또는 public)
     * 개선안: Lombok으로 간소화 가능 - @NoArgsConstructor(access = AccessLevel.PROTECTED)
     *
     * 참고 링크:
     *   - https://docs.jboss.org/hibernate/orm/6.0/userguide/html_single/Hibernate_User_Guide.html#entity-pojo
     */
    protected Product() {
    }

    /**
     * [리뷰 29 - public 생성자 노출로 인한 캡슐화 약화]
     * 문제: public 생성자 직접 노출로 객체 생성 로직 통제 불가, 인자 순서 혼동 가능
     * 원인: 정적 팩토리 메서드 패턴과 빌더 패턴 미적용
     *
     * 개선안: @Builder + @AllArgsConstructor(PRIVATE) + 정적 팩토리 메서드
     *        - @Builder: 빌더 패턴으로 가독성 높은 객체 생성
     *        - @AllArgsConstructor(PRIVATE): 생성자 은닉
     *        - @NoArgsConstructor(PROTECTED): JPA 요구사항 충족
     *        - public static Product of(category, name): 간편한 생성 메서드 제공
     *
     * 전/후 비교:
     *   Before: new Product("전자제품", "노트북") → 인자 순서 혼동 가능
     *   After:  Product.of("전자제품", "노트북") 또는
     *           Product.builder().category("전자제품").name("노트북").build()
     *
     * 측정치: 코드 가독성 향상, 생성 로직 캡슐화, 필드 순서 혼동 방지
     *
     * 참고 링크:
     *   - https://tecoble.techcourse.co.kr/post/2020-05-26-static-factory-method/ (정적 팩토리 메서드)
     *   - https://johngrib.github.io/wiki/pattern/builder/ (빌더 패턴)
     */
    public Product(String category, String name) {
        this.category = category;
        this.name = name;
    }

    /**
     * [리뷰 28 - 중복 getter]
     * 문제: @Getter로 이미 생성되는 메서드를 수동 정의
     * 원인: Lombok 어노테이션 동작에 대한 혼란
     * 개선안: 아래 두 메서드 삭제
     */
    public String getCategory() {
        return category;
    }

    public String getName() {
        return name;
    }
}
