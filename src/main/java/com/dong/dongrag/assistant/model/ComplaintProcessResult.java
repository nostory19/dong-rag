package com.dong.dongrag.assistant.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ComplaintProcessResult implements Serializable {

    private TaskPlan taskPlan;

    private List<WorkerResult> workerResults;

    private ComplaintResponse complaintResponse;
}
