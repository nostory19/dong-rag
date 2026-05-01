package com.dong.dongrag.service;

import com.dong.dongrag.model.dto.qa.QaAskRequest;
import com.dong.dongrag.model.vo.QaAnswerVO;

public interface RagQaService {

    QaAnswerVO ask(QaAskRequest request);
}
