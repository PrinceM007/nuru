package com.nuru.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Decimal-safe money helpers. All amounts are {@link BigDecimal} — never float/double.
 * Storage is DECIMAL(19,4); input is validated to at most 4 decimal places.
 */
public final class Money {

  private Money() {
  }

  public static BigDecimal requirePositive(BigDecimal amount, String field) {
    requireScale(amount, field);
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new IllegalArgumentException(field + " must be greater than zero");
    }
    return amount;
  }

  public static BigDecimal requireNonNegative(BigDecimal amount, String field) {
    requireScale(amount, field);
    if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException(field + " must not be negative");
    }
    return amount;
  }

  public static BigDecimal requireNonZero(BigDecimal amount, String field) {
    requireScale(amount, field);
    if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
      throw new IllegalArgumentException(field + " must not be zero");
    }
    return amount;
  }

  private static void requireScale(BigDecimal amount, String field) {
    if (amount != null && amount.stripTrailingZeros().scale() > 4) {
      throw new IllegalArgumentException(field + " supports at most 4 decimal places");
    }
  }

  public static String requireCurrency(String currency, String expected) {
    if (currency == null || !currency.equalsIgnoreCase(expected)) {
      throw new IllegalArgumentException(
          "Currency must be " + expected + " (single-currency group)");
    }
    return expected;
  }

  /**
   * Splits a total into {@code parts} installments that sum EXACTLY to the total:
   * every part is rounded down to 2 dp and the remainder goes to the last part.
   */
  public static List<BigDecimal> splitEvenly(BigDecimal total, int parts) {
    if (parts < 1) {
      throw new IllegalArgumentException("parts must be >= 1");
    }
    List<BigDecimal> result = new ArrayList<>(parts);
    BigDecimal each = total.divide(BigDecimal.valueOf(parts), 2, RoundingMode.DOWN);
    BigDecimal assigned = BigDecimal.ZERO;
    for (int i = 0; i < parts - 1; i++) {
      result.add(each);
      assigned = assigned.add(each);
    }
    result.add(total.subtract(assigned));
    return result;
  }
}
