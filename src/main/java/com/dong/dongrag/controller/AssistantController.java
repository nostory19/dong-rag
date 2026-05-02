package com.dong.dongrag.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.dong.dongrag.common.BaseResponse;
import com.dong.dongrag.common.ResultUtils;
import com.dong.dongrag.assistant.service.ComplaintEvaluationService;
import com.dong.dongrag.model.dto.assistant.AssistantChatRequest;
import com.dong.dongrag.service.AssistantService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/assistant")
@SaCheckLogin
public class AssistantController {

    @Resource
    private AssistantService assistantService;

    @Resource
    private ComplaintEvaluationService complaintEvaluationService;

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<String> chat(@RequestBody AssistantChatRequest request) {
        return assistantService.chat(request);
    }

    @PostMapping("/eval/complaint")
    @SaCheckRole("admin")
    public BaseResponse<Map<String, Object>> evaluateComplaint(@RequestParam("groupId") Long groupId) {
        return ResultUtils.success(complaintEvaluationService.quickEvaluate(groupId));
    }
}
