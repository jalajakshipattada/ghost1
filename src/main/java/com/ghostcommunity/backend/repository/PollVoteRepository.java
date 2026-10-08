package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {
    boolean existsByPollIdAndUserId(Long pollId, Long userId);
}
