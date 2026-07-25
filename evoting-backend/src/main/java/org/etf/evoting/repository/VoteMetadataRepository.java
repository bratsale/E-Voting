package org.etf.evoting.repository;

import org.etf.evoting.model.VoteMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VoteMetadataRepository extends JpaRepository<VoteMetadata, Integer> {
    Optional<VoteMetadata> findByVoteId(Integer voteId);
}