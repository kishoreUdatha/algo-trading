package com.example.algo.common.exception;

public class BrokerException extends RuntimeException {
    private final String brokerName;
    private final String errorCode;

    public BrokerException(String brokerName, String errorCode, String message) {
        super(message);
        this.brokerName = brokerName;
        this.errorCode = errorCode;
    }

    public BrokerException(String brokerName, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.brokerName = brokerName;
        this.errorCode = errorCode;
    }

    public String getBrokerName() { return brokerName; }
    public String getErrorCode() { return errorCode; }
}