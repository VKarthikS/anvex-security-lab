package com.anvex.defender.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.anvex.defender.protection.SourceBlock;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:anvex-persistence-test;DB_CLOSE_DELAY=-1",
        "anvex.data-dir=./target/test-data-persistence"
})
class BlockPersistenceServiceTest {

    @Autowired
    private BlockPersistenceService blockPersistenceService;

    @Test
    void onlyUnexpiredBlocksAreRestored() {
        Instant now = Instant.now();
        SourceBlock active = new SourceBlock("block-active", "10.0.0.5", "repeated-guessing", "test", now,
                now.plus(30, ChronoUnit.SECONDS), null, null, SourceBlock.STATE_ACTIVE);
        SourceBlock expired = new SourceBlock("block-expired", "10.0.0.6", "repeated-guessing", "test",
                now.minus(120, ChronoUnit.SECONDS), now.minus(90, ChronoUnit.SECONDS), null, null,
                SourceBlock.STATE_ACTIVE);

        blockPersistenceService.save(active);
        blockPersistenceService.save(expired);

        List<SourceBlock> restored = blockPersistenceService.activeAt(now);
        assertThat(restored).extracting(SourceBlock::blockId).contains("block-active");
        assertThat(restored).extracting(SourceBlock::blockId).doesNotContain("block-expired");
    }

    @Test
    void releasingABlockUpdatesTheExistingRowInsteadOfFailing() {
        Instant now = Instant.now();
        SourceBlock created = new SourceBlock("block-release", "10.0.0.7", "repeated-guessing", "test", now,
                now.plus(30, ChronoUnit.SECONDS), null, null, SourceBlock.STATE_ACTIVE);
        SourceBlock released = new SourceBlock("block-release", "10.0.0.7", "repeated-guessing",
                "manual release", now, now.plus(30, ChronoUnit.SECONDS), now, "analyst",
                SourceBlock.STATE_RELEASED);

        blockPersistenceService.save(created);
        blockPersistenceService.save(released);

        assertThat(blockPersistenceService.activeAt(now)).extracting(SourceBlock::blockId)
                .doesNotContain("block-release");
    }
}
