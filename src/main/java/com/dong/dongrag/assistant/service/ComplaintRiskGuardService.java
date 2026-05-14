package com.dong.dongrag.assistant.service;

import com.dong.dongrag.assistant.model.ComplaintProcessResult;
import com.dong.dongrag.assistant.policy.TemplateAwareAgentOutputPolicy;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

@Service
public class ComplaintRiskGuardService {

    @Resource
    private TemplateAwareAgentOutputPolicy templateAwareAgentOutputPolicy;

    public void applyRiskPolicy(String userMessage, ComplaintProcessResult processResult) {
        templateAwareAgentOutputPolicy.applyComplaintLegacy(userMessage, processResult);
    }
}
