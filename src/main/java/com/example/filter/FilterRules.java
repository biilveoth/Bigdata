package com.example.filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;

final class FilterRules {
    final BigDecimal minAmount;
    final BigDecimal maxAmount;
    final LocalDate start;
    final LocalDate end;
    final Set<String> categories;
    final Set<String> statuses;
    final int minOrders;
    final BigDecimal minTotal;

    FilterRules(Configuration conf) {
        minAmount = money(conf.get("filter.min-amount", "500000"));
        maxAmount = money(conf.get("filter.max-amount", "2000000"));
        start = Order.parseDate(conf.get("filter.start-date", "2026-09-01"));
        end = Order.parseDate(conf.get("filter.end-date", "2026-09-30"));
        categories = values(conf.get("filter.categories", "Electronics,Books"));
        statuses = values(conf.get("filter.statuses", "COMPLETED"));
        minOrders = Integer.parseInt(conf.get("filter.min-orders", "3"));
        minTotal = money(conf.get("filter.min-total", "3000000"));
        if (minAmount.signum() < 0 || minAmount.compareTo(maxAmount) > 0
                || start.isAfter(end) || minOrders < 1 || minTotal.signum() < 0) {
            throw new IllegalArgumentException("Invalid range or group threshold");
        }
    }

    private static BigDecimal money(String value) {
        if (!value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Money must be nonnegative with at most two decimals");
        }
        return new BigDecimal(value);
    }

    private static Set<String> values(String input) {
        Set<String> result = new HashSet<>();
        for (String value : Arrays.asList(input.split(",", -1))) {
            if (value.trim().isEmpty()) throw new IllegalArgumentException("Empty option value");
            result.add(value.trim());
        }
        return result;
    }

    boolean matches(Order order) {
        return order.amount.compareTo(minAmount) >= 0 && order.amount.compareTo(maxAmount) <= 0
                && !order.date.isBefore(start) && !order.date.isAfter(end)
                && categories.contains(order.category) && statuses.contains(order.status);
    }
}
