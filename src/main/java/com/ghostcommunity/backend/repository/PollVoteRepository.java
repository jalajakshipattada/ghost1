package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    boolean existsByPollIdAndUserId(Long pollId, Long userId);
    Optional<PollVote> findByPollIdAndUserId(Long pollId, Long userId);
}
