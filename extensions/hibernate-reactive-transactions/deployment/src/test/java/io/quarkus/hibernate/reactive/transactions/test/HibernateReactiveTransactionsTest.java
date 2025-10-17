package io.quarkus.hibernate.reactive.transactions.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.transactions.deployment.RequestScopedSession;
import io.quarkus.hibernate.reactive.transactions.deployment.TransactionalInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

public class HibernateReactiveTransactionsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Hero.class, TransactionalInterceptor.class, RequestScopedSession.class)
                    .addAsResource("initialTransactionData.sql", "import.sql")
            )
            .withConfigurationResource("application.properties");

    @Inject
    Mutiny.SessionFactory entityManager;

    @Test
    @RunOnVertxContext
    public void testReactiveManualTransaction(UniAsserter asserter) {

        // initialTransactionData.sql
        Long previousHeroId = 60L;

        Uni<Hero> failingUpdate = entityManager.withTransaction(session -> {
            return updateHero(session, previousHeroId, "updatedName")
                    .onItem().invoke(h -> {
                        throw new RuntimeException("Failing update");
                    });
        });

        Uni<Hero> refreshedHero =
                failingUpdate.onFailure().recoverWithNull()
                        .chain(id -> entityManager.withTransaction(session -> findHero(session, previousHeroId)));

        asserter.assertThat(() -> refreshedHero, h -> {
            assertThat(h.name).isEqualTo("initialName");
        });

    }

    @Test
    @RunOnVertxContext
    @Transactional
    public void testReactiveAnnotationTransaction(UniAsserter asserter) {

        // initialTransactionData.sql
        Long previousHeroId = 50L;

        Uni<Hero> failingUpdate = entityManager.withSession(session -> {
            return updateHero(session, previousHeroId, "updatedName")
                    .onItem().invoke(h -> {
                        throw new RuntimeException("Failing update");
                    });
            });

        Uni<Hero> refreshedHero =
                failingUpdate.onFailure().recoverWithNull()
                        .chain(id -> entityManager.withTransaction(session -> findHero(session, previousHeroId)));

        asserter.assertThat(() -> refreshedHero, h -> {
            assertThat(h.name).isEqualTo("initialName");
        });

    }

    public Uni<Hero> updateHero(Mutiny.Session session, Long id, String newName) {
        return session.find(Hero.class, id)
                .map(h -> {
                    h.setName(newName);
                    return h;
                }).call(() -> session.flush());
    }

    public Uni<Hero> findHero(Mutiny.Session session, Long id) {
        return session.find(Hero.class, id);
    }

}
