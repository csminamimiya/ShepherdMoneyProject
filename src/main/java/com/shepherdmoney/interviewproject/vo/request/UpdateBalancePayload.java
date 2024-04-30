package com.shepherdmoney.interviewproject.vo.request;

import java.time.LocalDate;

import lombok.Data;

@Data
public class UpdateBalancePayload {

    private String creditCardNumber;
    
    private LocalDate balanceDate;

    private double balanceAmount;

    public String getCardNumber() {
        return creditCardNumber;
    }
    public LocalDate getDate() {
        return balanceDate;
    }

    public double getAmount() {
        return balanceAmount;
    }
}
