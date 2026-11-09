package com.anvex.defender.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * Keeps track of temporary source blocks and of the rearm policy.
 *
 * <p>Rearm policy: a block is never extended just because blocked traffic keeps arriving. After a
 * block expires naturally, or after an analyst releases it, the source has to produce <em>new</em>
 * qualifying activity (a sample after the rearm instant) before a new block can be created. Old
 * evidence therefore cannot instantly recreate the same block.</p>
 */
@Service
public class BlockRegistry {

    private final Map<String, SourceBlock> activeBlocks = new ConcurrentHashMap<>();
    private final Map<String, Instant> rearmEligibleAt = new ConcurrentHashMap<>();
    private final List<SourceBlock> history = new ArrayList<>();

    public Optional<SourceBlock> activeBlock(String sourceIp, Instant now) {
        SourceBlock block = activeBlocks.get(sourceIp);
        if (block == null) {
            return Optional.empty();
        }
        if (!block.isActiveAt(now)) {
            activeBlocks.remove(sourceIp, block);
            SourceBlock expired = block.expired();
            record(expired);
            rearmEligibleAt.put(rearmKey(sourceIp, block.ruleId()), block.expiry());
            return Optional.empty();
        }
        return Optional.of(block);
    }

    public SourceBlock createBlock(String sourceIp, String ruleId, String reason, Instant now, Duration duration) {
        SourceBlock block = new SourceBlock(
                UUID.randomUUID().toString(),
                sourceIp,
                ruleId,
                reason,
                now,
                now.plus(duration),
                null,
                null,
                SourceBlock.STATE_ACTIVE);
        activeBlocks.put(sourceIp, block);
        record(block);
        return block;
    }

    public Optional<SourceBlock> release(String sourceIp, Instant now, String actor, Duration cooldown) {
        SourceBlock block = activeBlocks.get(sourceIp);
        if (block == null) {
            return Optional.empty();
        }
        SourceBlock released = block.released(now, actor);
        activeBlocks.remove(sourceIp, block);
        record(released);
        rearmEligibleAt.put(rearmKey(sourceIp, block.ruleId()), now.plus(cooldown));
        return Optional.of(released);
    }

    /**
     * Whether a new block may be created for the source. A source in its rearm cooldown needs fresh
     * qualifying activity before it can be blocked again.
     */
    public boolean mayCreateBlock(String sourceIp, String ruleId, Instant now) {
        if (activeBlocks.containsKey(sourceIp)) {
            return false;
        }
        Instant eligibleAt = rearmEligibleAt.get(rearmKey(sourceIp, ruleId));
        return eligibleAt == null || !now.isBefore(eligibleAt);
    }

    /** The instant from which new qualifying activity is required for a new block. */
    public Optional<Instant> rearmInstant(String sourceIp, String ruleId) {
        return Optional.ofNullable(rearmEligibleAt.get(rearmKey(sourceIp, ruleId)));
    }

    public List<SourceBlock> activeBlocks(Instant now) {
        List<SourceBlock> active = new ArrayList<>();
        activeBlocks.forEach((sourceIp, block) -> {
            if (block.isActiveAt(now)) {
                active.add(block);
            }
        });
        return active;
    }

    public List<SourceBlock> history() {
        synchronized (history) {
            return List.copyOf(history);
        }
    }

    public void restore(SourceBlock block) {
        activeBlocks.put(block.sourceIp(), block);
        record(block);
    }

    public void clear() {
        activeBlocks.clear();
        rearmEligibleAt.clear();
        synchronized (history) {
            history.clear();
        }
    }

    private void record(SourceBlock block) {
        synchronized (history) {
            history.add(block);
            while (history.size() > 500) {
                history.remove(0);
            }
        }
    }

    private static String rearmKey(String sourceIp, String ruleId) {
        return sourceIp + '|' + ruleId;
    }
}
