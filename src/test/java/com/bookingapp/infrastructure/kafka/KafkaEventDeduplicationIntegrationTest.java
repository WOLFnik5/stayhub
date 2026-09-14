package com.bookingapp.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookingapp.persistence.inbox.ProcessedEventJpaRepository;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.bookingapp.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class KafkaEventDeduplicationIntegrationTest
        extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private ProcessedEventJpaRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void tryInsert_shouldAllowOnlyOneConcurrentConsumer()
            throws Exception {

        UUID eventId = UUID.randomUUID();
        String consumer = "telegram-notification";

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<Integer> first =
                    executor.submit(() -> {
                        await(start);
                        return tryInsert(eventId, consumer);
                    });

            Future<Integer> second =
                    executor.submit(() -> {
                        await(start);
                        return tryInsert(eventId, consumer);
                    });

            start.countDown();

            int firstResult = first.get();
            int secondResult = second.get();

            assertThat(firstResult + secondResult)
                    .isEqualTo(1);

            assertThat(repository.count())
                    .isEqualTo(1);

        } finally {
            executor.shutdownNow();
        }
    }

    private int tryInsert(
            UUID eventId,
            String consumer
    ) {
        TransactionTemplate transaction =
                new TransactionTemplate(transactionManager);

        return transaction.execute(status ->
                repository.tryInsert(eventId, consumer)
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Interrupted while waiting",
                    exception
            );
        }
    }
}