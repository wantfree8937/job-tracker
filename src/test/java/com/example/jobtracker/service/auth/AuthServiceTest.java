package com.example.jobtracker.service.auth;

import com.example.jobtracker.exception.ProfileParseFailedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextBox;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AuthService의 순수 함수(외부 호출 없이 검증 가능한 부분) 단위 테스트 */
class AuthServiceTest {

    @Test
    void script와_style_태그를_제거하고_본문_텍스트만_추출한다() {
        String html = "<html><body><script>alert(1)</script><style>.a{}</style>"
                + "<p>3년차 백엔드 개발자입니다.</p></body></html>";

        String text = AuthService.extractBodyText(Jsoup.parse(html));

        assertThat(text).isEqualTo("3년차 백엔드 개발자입니다.");
    }

    @Test
    void 본문이_5000자를_넘으면_잘라낸다() {
        String html = "<html><body>" + "가".repeat(6000) + "</body></html>";

        String text = AuthService.extractBodyText(Jsoup.parse(html));

        assertThat(text).hasSize(5000);
    }

    @Test
    void PDF_파일에서_텍스트를_추출한다() throws Exception {
        AuthService authService = new AuthService(null, null, null);
        byte[] pdfBytes = createPdfWithText("Backend Developer Resume");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pdf", "application/pdf", pdfBytes);

        var response = authService.parseProfilePdf(file);

        assertThat(response.text()).contains("Backend Developer Resume");
    }

    @Test
    void PDF가_아닌_파일은_예외를_던진다() {
        AuthService authService = new AuthService(null, null, null);
        MockMultipartFile file = new MockMultipartFile("file", "resume.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> authService.parseProfilePdf(file))
                .isInstanceOf(ProfileParseFailedException.class);
    }

    @Test
    void PPTX_파일에서_텍스트를_추출한다() throws Exception {
        AuthService authService = new AuthService(null, null, null);
        byte[] pptxBytes = createPptxWithText("Backend Developer Resume");
        MockMultipartFile file = new MockMultipartFile("file", "resume.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", pptxBytes);

        var response = authService.parseProfilePdf(file);

        assertThat(response.text()).contains("Backend Developer Resume");
    }

    @Test
    void PPT_파일에서_텍스트를_추출한다() throws Exception {
        AuthService authService = new AuthService(null, null, null);
        byte[] pptBytes = createPptWithText("Backend Developer Resume");
        MockMultipartFile file = new MockMultipartFile("file", "resume.ppt",
                "application/vnd.ms-powerpoint", pptBytes);

        var response = authService.parseProfilePdf(file);

        assertThat(response.text()).contains("Backend Developer Resume");
    }

    @Test
    void 화이트리스트에_없는_도메인_URL은_예외를_던진다() {
        AuthService authService = new AuthService(null, null, null);

        assertThatThrownBy(() -> authService.parseProfileUrl("https://example.com/resume"))
                .isInstanceOf(ProfileParseFailedException.class);
    }

    private byte[] createPdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(text);
                stream.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private byte[] createPptxWithText(String text) throws Exception {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            XSLFSlide slide = ppt.createSlide();
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setText(text);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private byte[] createPptWithText(String text) throws Exception {
        try (HSLFSlideShow ppt = new HSLFSlideShow()) {
            HSLFSlide slide = ppt.createSlide();
            HSLFTextBox textBox = new HSLFTextBox();
            textBox.setText(text);
            slide.addShape(textBox);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        }
    }
}
