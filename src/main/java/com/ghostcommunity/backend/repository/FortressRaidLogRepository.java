package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.FortressRaidLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FortressRaidLogRepository extends JpaRepository<FortressRaidLog, Long> {
    List<FortressRaidLog> findTop20ByTargetSectionIdOrAttackerSectionIdOrderByTimestampDesc(Long targetSectionId, Long attackerSectionId);
}
