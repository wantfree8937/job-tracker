package com.example.jobtracker.util;

import com.example.jobtracker.exception.ProfileParseFailedException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.usermodel.HSLFSlide;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

/** 이력서 파일(PDF/PPT/PPTX)에서 텍스트를 추출한다. 업로드 시(AuthService)와 AI 면접 질문 생성 시(AiService) 공용 */
public final class ResumeTextExtractor {

    private static final int PROFILE_TEXT_MAX_LENGTH = 5000;
    private static final String PPTX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String PPT_CONTENT_TYPE = "application/vnd.ms-powerpoint";

    private ResumeTextExtractor() {
    }

    // 업로드된 파일에서 텍스트 추출 (최대 5000자)
    public static String extractResumeText(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ProfileParseFailedException("PDF/PPT/PPTX 파일만 업로드할 수 있습니다");
        }
        try {
            return extract(file.getInputStream(), file.getContentType(), file.getOriginalFilename());
        } catch (ProfileParseFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ProfileParseFailedException("파일에서 텍스트를 추출할 수 없습니다");
        }
    }

    // 저장된 이력서 원본(byte[])에서 텍스트 추출, AI 면접 질문 생성 시 profileText가 비어있을 때 사용
    public static String extractResumeText(byte[] bytes, String contentType, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new ProfileParseFailedException("PDF/PPT/PPTX 파일만 업로드할 수 있습니다");
        }
        try {
            return extract(new ByteArrayInputStream(bytes), contentType, filename);
        } catch (ProfileParseFailedException e) {
            throw e;
        } catch (Exception e) {
            throw new ProfileParseFailedException("파일에서 텍스트를 추출할 수 없습니다");
        }
    }

    private static String extract(InputStream inputStream, String contentType, String originalFilename) throws Exception {
        String filename = originalFilename == null ? "" : originalFilename.toLowerCase();

        try (InputStream is = inputStream) {
            if ("application/pdf".equals(contentType) || filename.endsWith(".pdf")) {
                return extractPdfText(is);
            }
            if (PPTX_CONTENT_TYPE.equals(contentType) || filename.endsWith(".pptx")) {
                return extractPptxText(is);
            }
            if (PPT_CONTENT_TYPE.equals(contentType) || filename.endsWith(".ppt")) {
                return extractPptText(is);
            }
        }
        throw new ProfileParseFailedException("PDF/PPT/PPTX 파일만 업로드할 수 있습니다");
    }

    private static String extractPdfText(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            return truncate(new PDFTextStripper().getText(document));
        }
    }

    private static String extractPptxText(InputStream inputStream) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XSLFSlide slide : slideShow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        sb.append(textShape.getText()).append('\n');
                    }
                }
            }
            return truncate(sb.toString());
        }
    }

    private static String extractPptText(InputStream inputStream) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (HSLFSlide slide : slideShow.getSlides()) {
                for (List<HSLFTextParagraph> paragraphs : slide.getTextParagraphs()) {
                    sb.append(HSLFTextParagraph.getText(paragraphs)).append('\n');
                }
            }
            return truncate(sb.toString());
        }
    }

    public static String truncate(String text) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed.length() > PROFILE_TEXT_MAX_LENGTH ? trimmed.substring(0, PROFILE_TEXT_MAX_LENGTH) : trimmed;
    }
}
