package com.aceerp.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BillPaymentRow(
    int paymentId,
    String paymentNumber,
    LocalDate paymentDate,
    String vendorName,
    String billNumber,
    String paymentMethod,
    String referenceNumber,
    BigDecimal amountPaid,
    String notes
) {}