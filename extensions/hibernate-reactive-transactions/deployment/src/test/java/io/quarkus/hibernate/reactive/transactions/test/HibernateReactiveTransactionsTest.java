package io.quarkus.hibernate.reactive.transactions.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.transactions.runtime.TransactionalInterceptorRequired;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

public class HibernateReactiveTransactionsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Hero.class, TransactionalInterceptorRequired.class)
                    .addAsResource("initialTransactionData.sql", "import.sql"))
            .withConfigurationResource("application.properties");

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    public void testReactiveManualTransaction(UniAsserter asserter) {

        // initialTransactionData.sql
        Long previousHeroId = 60L;

        // First update, make sure it's committed
        Uni<Hero> committedUpdate = sessionFactory.withTransaction(
                session -> updateHero(session, previousHeroId, "updatedNameCommitted"));

        Uni<Hero> refreshedCommitted = sessionFactory.withTransaction(session ->
            committedUpdate
                .chain(id -> session.find(Hero.class, previousHeroId)));

        asserter.assertThat(() -> refreshedCommitted, h -> {
            assertThat(h.name).isEqualTo("updatedNameCommitted");
        });

        // Second update, make sure it's rollbacked
        Uni<Hero> failingUpdate = committedUpdate.flatMap(c -> {
            return sessionFactory.withTransaction(session -> {
                return updateHero(session, previousHeroId, "this name won't appear")
                        .onItem().invoke(h -> {
                            throw new RuntimeException("Failing update");
                        });
            });
        });

        Uni<Hero> refreshedHeroAfterRollback = failingUpdate.onFailure().recoverWithNull()
                .chain(id -> sessionFactory.withTransaction(session -> session.find(Hero.class, previousHeroId)));

        asserter.assertThat(() -> refreshedHeroAfterRollback, h -> {
            assertThat(h.name).isEqualTo("updatedNameCommitted");
        });

    }

    @Inject
    Mutiny.Session session;

    /*
     * This is the same test as #testReactiveManualTransaction but instead of manually calling sessionFactory.withTransaction
     * We use the annotation @Transactional
     */
    @Test
    @RunOnVertxContext
    public void testReactiveAnnotationTransaction(UniAsserter asserter) {

        // initialTransactionData.sql
        Long previousHeroId = 50L;

        // First update, make sure it's committed
        Uni<Hero> committedUpdate = updateWithCommit(previousHeroId, "updatedNameCommitted");

        Uni<Hero> refreshAfterCommit = committedUpdate.chain( h -> refreshHero(previousHeroId));

        asserter.assertThat(() -> refreshAfterCommit, h -> {
            assertThat(h.name).isEqualTo("updatedNameCommitted");
            System.out.println("First Assertion made");
        });

        // Second update, make sure it's rollbacked
        Uni<Hero> failingUpdate = refreshAfterCommit.flatMap(h -> {
            return transactionalUpdateWithRollback(previousHeroId, "this name won't appear");
        }).onFailure().recoverWithNull();

        Uni<Hero> refreshedHero = failingUpdate.chain(h -> refreshHero2(previousHeroId));

        asserter.assertThat(() -> refreshedHero, h -> {
            assertThat(h.name).isEqualTo("updatedNameCommitted");
            System.out.println("Second Assertion made");

        });

    }

    @Transactional
    public Uni<Hero> refreshHero(Long previousHeroId) {
        System.out.println("Reload hero");
        return session.find(Hero.class, previousHeroId);
    }

    @Transactional
    public Uni<Hero> refreshHero2(Long previousHeroId) {
        System.out.println("Reload hero");
        return session.find(Hero.class, previousHeroId);
    }

    @Transactional
    public Uni<Hero> updateWithCommit(Long previousHeroId, String newName) {
        return updateHero(session, previousHeroId, newName);
    }

    @Transactional
    public Uni<Hero> transactionalUpdateWithRollback(Long previousHeroId, String newName) {
        return updateHero(session, previousHeroId, newName)
                .onItem().invoke(h -> {
                    // Questo metodo non viene mai chiamato! Non viene veramente fatto il rollback come mai?
                    System.out.println("About to call the exception");
                    throw new RuntimeException("Failing update");
                });
    }

    public Uni<Hero> updateHero(Mutiny.Session session, Long id, String newName) {
        return session.find(Hero.class, id)
                .map(h -> {
                    System.out.println("Update hero with " + newName);
                    h.setName(newName);
                    return h;
                }).call(() -> session.flush());
    }
}
