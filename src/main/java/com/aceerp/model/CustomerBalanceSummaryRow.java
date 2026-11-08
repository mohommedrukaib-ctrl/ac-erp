package com.aceerp.model;

import java.math.BigDecimal;

public record CustomerBalanceSummaryRow(
    int customerId,
    String customerName,
    String phone,
    BigDecimal totalInvoiced,
    BigDecimal totalPaid,
    BigDecimal balanceDue
) {}