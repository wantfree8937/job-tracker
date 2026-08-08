package com.example.jobtracker.service.job;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** CollectedJobService의 순수 함수(외부 호출 없이 검증 가능한 부분) 단위 테스트 */
class CollectedJobServiceTest {

    @Test
    void 정상적인_http_URL은_그대로_통과한다() {
        URI uri = CollectedJobService.validateUrl("https://example.com/job/1");

        assertThat(uri).isEqualTo(URI.create("https://example.com/job/1"));
    }

    @Test
    void http_https가_아니면_예외를_던진다() {
        assertThatThrownBy(() -> CollectedJobService.validateUrl("ftp://example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void localhost는_차단한다() {
        assertThatThrownBy(() -> CollectedJobService.validateUrl("http://localhost:8080"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 사설_IP_대역은_차단한다() {
        assertThatThrownBy(() -> CollectedJobService.validateUrl("http://192.168.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CollectedJobService.validateUrl("http://10.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 잘못된_형식의_URL은_예외를_던진다() {
        assertThatThrownBy(() -> CollectedJobService.validateUrl("https://exa mple.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void property가_content보다_먼저인_메타태그를_추출한다() {
        String html = "<meta property=\"og:title\" content=\"백엔드 개발자\">";

        assertThat(CollectedJobService.extractMeta(html, "og:title")).isEqualTo("백엔드 개발자");
    }

    @Test
    void content가_property보다_먼저인_메타태그도_추출한다() {
        String html = "<meta content=\"토스\" property=\"og:site_name\">";

        assertThat(CollectedJobService.extractMeta(html, "og:site_name")).isEqualTo("토스");
    }

    @Test
    void 매칭되는_메타태그가_없으면_null을_반환한다() {
        assertThat(CollectedJobService.extractMeta("<html></html>", "og:title")).isNull();
    }
}
