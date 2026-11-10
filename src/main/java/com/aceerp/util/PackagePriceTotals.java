package com.aceerp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PackagePriceTotals {
    private PackagePriceTotals() {}

    public static Totals apply(BigDecimal lineSubtotal, BigDecimal lineTax,
                               boolean enabled, BigDecimal packageAmount) {
        BigDecimal subtotal = lineSubtotal != null ? lineSubtotal : BigDecimal.ZERO;
        BigDecimal tax = lineTax != null ? lineTax : BigDecimal.ZERO;
        if (!enabled) return new Totals(subtotal, tax);

        BigDecimal amount = packageAmount != null ? packageAmount : BigDecimal.ZERO;
        if (subtotal.signum() > 0) {
            tax = tax.multiply(amount).divide(subtotal, 2, RoundingMode.HALF_UP);
        } else {
            tax = BigDecimal.ZERO;
        }
        return new Totals(amount, tax);
    }

    public record Totals(BigDecimal subtotal, BigDecimal tax) {}
}
