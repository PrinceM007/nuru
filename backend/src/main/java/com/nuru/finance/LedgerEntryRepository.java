package com.nuru.finance;

import com.nuru.domain.LedgerKind;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

  Page<LedgerEntry> findByGroupIdOrderByOccurredAtDesc(UUID groupId, Pageable pageable);

  @Query("""
      select coalesce(sum(e.amount), 0) from LedgerEntry e
      where e.groupId = :groupId and e.kind in :kinds
      """)
  BigDecimal sumByGroupAndKinds(UUID groupId, List<LedgerKind> kinds);
}
