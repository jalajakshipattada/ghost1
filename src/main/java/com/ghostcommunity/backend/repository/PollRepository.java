package com.ghostcommunity.backend.repository;

import com.ghostcommunity.backend.model.Poll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PollRepository extends JpaRepository<Poll, Long> {}
