package com.aceerp.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class ReportDateRangeUtil {

    public enum DatePreset {
        TODAY("Today"),
        THIS_WEEK("This Week"),
        THIS_MONTH("This Month"),
        THIS_QUARTER("This Quarter"),
        THIS_YEAR("This Year"),
        CUSTOM("Custom Range");

        private final String label;

        DatePreset(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public record DateRange(LocalDate startDate, LocalDate endDate) {}

    public static DateRange getRangeForPreset(DatePreset preset) {
        LocalDate today = LocalDate.now();

        return switch (preset) {
            case TODAY -> new DateRange(today, today);
            case THIS_WEEK -> new DateRange(
                    today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            );
            case THIS_MONTH -> new DateRange(
                    today.with(TemporalAdjusters.firstDayOfMonth()),
                    today.with(TemporalAdjusters.lastDayOfMonth())
            );
            case THIS_QUARTER -> {
                int currentMonth = today.getMonthValue();
                int firstMonthOfQuarter = ((currentMonth - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(today.getYear(), firstMonthOfQuarter, 1);
                LocalDate end = start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
                yield new DateRange(start, end);
            }
            case THIS_YEAR -> new DateRange(
                    today.with(TemporalAdjusters.firstDayOfYear()),
                    today.with(TemporalAdjusters.lastDayOfYear())
            );
            case CUSTOM -> new DateRange(
                    today.with(TemporalAdjusters.firstDayOfMonth()),
                    today
            );
        };
    }
}