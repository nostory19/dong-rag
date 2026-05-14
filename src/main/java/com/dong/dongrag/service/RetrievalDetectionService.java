package com.dong.dongrag.service;

import com.dong.dongrag.model.dto.retrieval.RetrievalDetectRequest;
import com.dong.dongrag.model.vo.RetrievalDetectResponseVO;

public interface RetrievalDetectionService {

    RetrievalDetectResponseVO detect(RetrievalDetectRequest request);
}
