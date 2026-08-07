package com.example.jobtracker.controller.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 배포 플랫폼(Render 등) 헬스체크용 엔드포인트.
 * 인증 없이 200을 반환해야 "서버 살아있음"으로 판정된다.
 * (인증이 필요한 API를 헬스체크로 쓰면 401 → 배포 실패로 오판됨)
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
