package com.nuru.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void splitEvenlySumsExactlyToTotal() {
    List<BigDecimal> parts = Money.splitEvenly(new BigDecimal("100.00"), 3);
    assertThat(parts).hasSize(3);
    assertThat(parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo(new BigDecimal("100.00"));
    // 33.33 + 33.33 + 33.34
    assertThat(parts.get(2)).isEqualByComparingTo(new BigDecimal("33.34"));
  }

  @Test
  void splitEvenlyHandlesExactDivision() {
    List<BigDecimal> parts = Money.splitEvenly(new BigDecimal("13200.00"), 12);
    assertThat(parts).allMatch(p -> p.compareTo(new BigDecimal("1100.00")) == 0);
  }

  @Test
  void requirePositiveRejectsZeroAndNegative() {
    assertThatThrownBy(() -> Money.requirePositive(BigDecimal.ZERO, "amount"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("-1"), "amount"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void moreThanFourDecimalPlacesRejected() {
    assertThatThrownBy(() -> Money.requirePositive(new BigDecimal("1.23456"), "amount"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
