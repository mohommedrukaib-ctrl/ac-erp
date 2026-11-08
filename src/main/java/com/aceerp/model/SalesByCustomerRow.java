package com.aceerp.model;

import java.math.BigDecimal;

public record SalesByCustomerRow(
    int customerId,
    String customerName,
    int invoiceCount,
    BigDecimal totalSubtotal,
    BigDecimal totalTax,
    BigDecimal totalSales,
    double percentOfTotal
) {}