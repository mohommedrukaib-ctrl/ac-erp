package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ArAgingDetailRow(
    String customerName,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    long daysOverdue,
    String agingBucket,
    BigDecimal originalAmount,
    BigDecimal amountPaid,
    BigDecimal balanceDue
) {}