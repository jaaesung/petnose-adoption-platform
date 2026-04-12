package com.petnose.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring 컨텍스트 기동 최소 검증 테스트.
 * H2 인메모리 DB를 사용하므로 MySQL 없이 실행 가능합니다.
 * QdrantInitializer는 연결 실패 시 경고만 출력하고 계속 기동합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class PetNoseApplicationTests {

    @Test
    void contextLoads() {
        // Spring 컨텍스트가 오류 없이 로드되는지 확인
    }
}
