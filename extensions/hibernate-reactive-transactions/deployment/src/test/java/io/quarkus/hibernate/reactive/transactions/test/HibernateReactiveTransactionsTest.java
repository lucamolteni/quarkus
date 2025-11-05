package io.quarkus.hibernate.reactive.transactions.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.transactions.runtime.TransactionalInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

public class HibernateReactiveTransactionsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Hero.class, TransactionalInterceptor.class)
                    .addAsResource("initialTransactionData.sql", "import.sql"))
            .withConfigurationResource("application.properties");

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Test
    @RunOnVertxContext
    public void testReactiveManualTransaction(UniAsserter asserter) {

        // initialTransactionData.sql
        Long previousHeroId = 60L;

        Uni<Hero> failingUpdate = sessionFactory.withTransaction(session -> {
            return updateHero(session, previousHeroId, "updatedName")
                    .onItem().invoke(h -> {
                        throw new RuntimeException("Failing update");
                    });
        });

        Uni<Hero> refreshedHero = failingUpdate.onFailure().recoverWithNull()
                .chain(id -> sessionFactory.withTransaction(session -> session.find(Hero.class, previousHeroId)));

        asserter.assertThat(() -> refreshedHero, h -> {
            assertThat(h.name).isEqualTo("initialName");
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

        // We need to wrap the test in a method because to enable Transactions the method should return a Uni
        Uni<Hero> failingUpdate = transactionalUpdateWithRollback(previousHeroId);

        assertHeroIsRollbackInAnotherTransaction(asserter, failingUpdate, previousHeroId);

    }

    @Transactional
    public void assertHeroIsRollbackInAnotherTransaction(UniAsserter asserter, Uni<Hero> failingUpdate, Long previousHeroId) {
        Uni<Hero> refreshedHero = failingUpdate.onFailure().recoverWithNull()
                .chain(id -> session.find(Hero.class, previousHeroId));

        asserter.assertThat(() -> refreshedHero, h -> {
            assertThat(h.name).isEqualTo("initialName");
        });
    }

    @Transactional
    public Uni<Hero> transactionalUpdateWithRollback(Long previousHeroId) {
        return updateHero(session, previousHeroId, "updatedName")
                .onItem().invoke(h -> {
                    throw new RuntimeException("Failing update");
                });
    }

    public Uni<Hero> updateHero(Mutiny.Session session, Long id, String newName) {
        return session.find(Hero.class, id)
                .map(h -> {
                    h.setName(newName);
                    return h;
                }).call(() -> session.flush());
    }
}
