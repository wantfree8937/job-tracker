package com.example.jobtracker.controller.ai;

import com.example.jobtracker.dto.ai.InterviewQuestionRequest;
import com.example.jobtracker.dto.ai.InterviewQuestionResponse;
import com.example.jobtracker.service.ai.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/interview/questions")
    public ResponseEntity<InterviewQuestionResponse> generateInterviewQuestions(
            @RequestBody InterviewQuestionRequest request) {
        return ResponseEntity.ok(aiService.generateQuestions(request));
    }
}
