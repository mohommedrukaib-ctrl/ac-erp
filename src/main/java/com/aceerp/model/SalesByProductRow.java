package com.aceerp.model;

import java.math.BigDecimal;

public record SalesByProductRow(
    String productCode,
    String productName,
    String category,
    BigDecimal totalQuantitySold,
    BigDecimal totalSalesAmount,
    BigDecimal averagePrice,
    double percentOfTotal
) {}