package com.peterscode.edutrack.examination.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record WithdrawRequest(String reason) {}
