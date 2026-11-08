package com.aceerp.model;

import java.math.BigDecimal;

public record VendorBalanceSummaryRow(
    int supplierId,
    String vendorName,
    String phone,
    BigDecimal totalBilled,
    BigDecimal totalPaid,
    BigDecimal balanceOwed
) {}