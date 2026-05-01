package com.dong.dongrag.assistant.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ComplaintResponse implements Serializable {

    private String reply;

    private List<String> actions;

    private boolean humanHandoff;

    private String escalationReason;
}
