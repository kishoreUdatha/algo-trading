package com.example.algo.common.exception;

public class RiskManagementException extends RuntimeException {
    private final String riskRule;

    public RiskManagementException(String riskRule, String message) {
        super(message);
        this.riskRule = riskRule;
    }

    public String getRiskRule() { return riskRule; }
}