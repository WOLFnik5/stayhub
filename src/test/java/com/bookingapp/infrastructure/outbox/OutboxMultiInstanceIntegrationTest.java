package com.bookingapp.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingapp.persistence.outbox.OutboxEventEntity;
import com.bookingapp.persistence.outbox.OutboxEventJpaRepository;
import com.bookingapp.persistence.outbox.OutboxStatus;
import com.bookingapp.testsupport.PostgreSqlIntegrationTestSupport;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class OutboxMultiInstanceIntegrationTest
        extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private OutboxEventJpaRepository outboxEventJpaRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OutboxTransactionService outboxTransactionService;

    @BeforeEach
    void cleanOutbox() {
        outboxEventJpaRepository.deleteAll();
        outboxEventJpaRepository.flush();
    }

    @Test
    void claimBatch_shouldNotClaimSameEventsAcrossConcurrentWorkers()
            throws Exception {

        int totalEvents = 10;
        int batchSize = 5;

        for (int i = 0; i < totalEvents; i++) {
            outboxEventJpaRepository.save(
                    OutboxEventEntity.newEvent(
                            "Booking",
                            (long) i,
                            "BookingCreatedEvent",
                            "booking-created",
                            String.valueOf(i),
                            "{\"id\":" + i + "}"
                    )
            );
        }

        outboxEventJpaRepository.flush();

        CountDownLatch firstWorkerHasLockedRows =
                new CountDownLatch(1);

        CountDownLatch allowFirstWorkerToCommit =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<List<UUID>> workerA = executor.submit(() -> {
                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                return transaction.execute(status -> {
                    List<OutboxEventEntity> events =
                            outboxEventJpaRepository
                                    .findBatchForProcessing(batchSize);

                    events.forEach(
                            OutboxEventEntity::markProcessing
                    );

                    List<UUID> ids = events.stream()
                            .map(OutboxEventEntity::getId)
                            .toList();

                    firstWorkerHasLockedRows.countDown();

                    await(allowFirstWorkerToCommit);

                    return ids;
                });
            });

            Future<List<UUID>> workerB = executor.submit(() -> {
                await(firstWorkerHasLockedRows);

                TransactionTemplate transaction =
                        new TransactionTemplate(transactionManager);

                List<UUID> result = transaction.execute(status -> {
                    List<OutboxEventEntity> events =
                            outboxEventJpaRepository
                                    .findBatchForProcessing(batchSize);

                    events.forEach(
                            OutboxEventEntity::markProcessing
                    );

                    return events.stream()
                            .map(OutboxEventEntity::getId)
                            .toList();
                });

                allowFirstWorkerToCommit.countDown();

                return result;
            });

            List<UUID> firstBatch = workerA.get();
            List<UUID> secondBatch = workerB.get();

            assertThat(firstBatch)
                    .hasSize(batchSize);

            assertThat(secondBatch)
                    .hasSize(batchSize);

            Set<UUID> intersection =
                    new HashSet<>(firstBatch);

            intersection.retainAll(secondBatch);

            assertThat(intersection)
                    .as("Concurrent workers must not claim the same events")
                    .isEmpty();

            Set<UUID> allClaimed =
                    new HashSet<>(firstBatch);

            allClaimed.addAll(secondBatch);

            assertThat(allClaimed)
                    .hasSize(totalEvents);

            List<OutboxEventEntity> persisted =
                    outboxEventJpaRepository.findAll();

            assertThat(persisted)
                    .hasSize(totalEvents)
                    .allMatch(event ->
                            event.getStatus()
                                    == OutboxStatus.PROCESSING
                    );

        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while coordinating concurrent workers",
                    exception
            );
        }
    }

    @Test
    void recoverStaleClaims_shouldReleaseOnlyExpiredProcessingEvents() {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        UUID staleId = transaction.execute(status -> {
            OutboxEventEntity event =
                    OutboxEventEntity.newEvent(
                            "Booking",
                            1L,
                            "BookingCreatedEvent",
                            "booking-created",
                            "1",
                            "{}"
                    );

            event.markProcessing();

            /*
             * For this test it is convenient to set an old lease manually.
             */
            event.setClaimedAt(
                    java.time.LocalDateTime.now()
                            .minusMinutes(30)
            );

            return outboxEventJpaRepository
                    .saveAndFlush(event)
                    .getId();
        });

        UUID freshId = transaction.execute(status -> {
            OutboxEventEntity event =
                    OutboxEventEntity.newEvent(
                            "Booking",
                            2L,
                            "BookingCreatedEvent",
                            "booking-created",
                            "2",
                            "{}"
                    );

            event.markProcessing();

            return outboxEventJpaRepository
                    .saveAndFlush(event)
                    .getId();
        });

        int recovered = outboxTransactionService.recoverStaleClaims(
                java.time.LocalDateTime.now()
                        .minusMinutes(10)
        );

        assertThat(recovered).isEqualTo(1);

        OutboxEventEntity stale =
                outboxEventJpaRepository
                        .findById(staleId)
                        .orElseThrow();

        OutboxEventEntity fresh =
                outboxEventJpaRepository
                        .findById(freshId)
                        .orElseThrow();

        assertThat(stale.getStatus())
                .isEqualTo(OutboxStatus.FAILED);

        assertThat(stale.getClaimedAt())
                .isNull();

        assertThat(fresh.getStatus())
                .isEqualTo(OutboxStatus.PROCESSING);

        assertThat(fresh.getClaimedAt())
                .isNotNull();
    }

    @Test
    void staleWorker_shouldNotCompleteANewerClaim() {
        OutboxEventEntity event = outboxEventJpaRepository.saveAndFlush(
                OutboxEventEntity.newEvent(
                        "Booking", 3L, "BookingCreatedEvent",
                        "booking-created", "3", "{}"
                )
        );

        OutboxEventEntity firstClaim = outboxTransactionService.claimBatch(1).getFirst();
        UUID staleToken = firstClaim.getClaimToken();
        firstClaim.setClaimedAt(java.time.LocalDateTime.now().minusMinutes(30));
        outboxEventJpaRepository.saveAndFlush(firstClaim);

        outboxTransactionService.recoverStaleClaims(
                java.time.LocalDateTime.now().minusMinutes(10)
        );
        OutboxEventEntity secondClaim = outboxTransactionService.claimBatch(1).getFirst();

        assertThat(secondClaim.getClaimToken()).isNotEqualTo(staleToken);
        assertThat(outboxTransactionService.markSent(event.getId(), staleToken)).isFalse();
        assertThat(outboxTransactionService.markSent(
                event.getId(), secondClaim.getClaimToken())).isTrue();
        assertThat(outboxEventJpaRepository.findById(event.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.SENT);
    }
}
