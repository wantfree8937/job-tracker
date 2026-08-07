package com.example.jobtracker;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// test 프로필(H2 인메모리)로 격리 — CI(GitHub Actions)에 PostgreSQL이 없어도 통과
@SpringBootTest
@ActiveProfiles("test")
class JobTrackerApplicationTests {

	@Test
	void contextLoads() {
	}

}
