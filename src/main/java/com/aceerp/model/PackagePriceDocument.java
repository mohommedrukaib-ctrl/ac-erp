package com.aceerp.model;

import java.math.BigDecimal;

public interface PackagePriceDocument {
    boolean isPackagePriceEnabled();
    void setPackagePriceEnabled(boolean enabled);
    BigDecimal getPackagePriceAmount();
    void setPackagePriceAmount(BigDecimal amount);
}
