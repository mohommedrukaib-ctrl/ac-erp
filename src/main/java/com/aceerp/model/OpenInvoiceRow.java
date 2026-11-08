package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record OpenInvoiceRow(
    int invoiceId,
    String invoiceNumber,
    String customerName,
    LocalDate invoiceDate,
    LocalDate dueDate,
    long daysOverdue,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal balanceDue,
    String status
) {}