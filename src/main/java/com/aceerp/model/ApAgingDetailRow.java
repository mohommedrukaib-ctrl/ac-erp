package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ApAgingDetailRow(
    String vendorName,
    String billNumber,
    LocalDate billDate,
    LocalDate dueDate,
    long daysOverdue,
    String agingBucket,
    BigDecimal originalAmount,
    BigDecimal amountPaid,
    BigDecimal balanceDue
) {}