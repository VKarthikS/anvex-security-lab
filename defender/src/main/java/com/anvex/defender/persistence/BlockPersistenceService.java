package com.anvex.defender.persistence;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.anvex.defender.protection.SourceBlock;

/**
 * Synchronous persistence for block transitions. A block is written before the triggering request
 * is completed, so a restart cannot lose a restriction that was already reported to a client.
 */
@Service
public class BlockPersistenceService {

    private final SourceBlockRepository sourceBlockRepository;

    public BlockPersistenceService(SourceBlockRepository sourceBlockRepository) {
        this.sourceBlockRepository = sourceBlockRepository;
    }

    @Transactional
    public void save(SourceBlock block) {
        sourceBlockRepository.findByBlockId(block.blockId()).ifPresentOrElse(existing -> {
            existing.applyTransition(block.reason(), block.expiry(), block.releasedAt(), block.releasedBy(),
                    block.state());
            sourceBlockRepository.save(existing);
        }, () -> sourceBlockRepository.save(new Entities.SourceBlockEntity(
                block.blockId(),
                block.sourceIp(),
                block.ruleId(),
                block.reason(),
                block.createdAt(),
                block.expiry(),
                block.releasedAt(),
                block.releasedBy(),
                block.state())));
    }

    @Transactional(readOnly = true)
    public List<SourceBlock> activeAt(Instant now) {
        return sourceBlockRepository.findByState(SourceBlock.STATE_ACTIVE).stream()
                .filter(entity -> entity.getExpiry().isAfter(now))
                .map(entity -> new SourceBlock(
                        entity.getBlockId(),
                        entity.getSourceIp(),
                        entity.getRuleId(),
                        entity.getReason(),
                        entity.getCreatedAt(),
                        entity.getExpiry(),
                        entity.getReleasedAt(),
                        entity.getReleasedBy(),
                        entity.getState()))
                .toList();
    }
}
