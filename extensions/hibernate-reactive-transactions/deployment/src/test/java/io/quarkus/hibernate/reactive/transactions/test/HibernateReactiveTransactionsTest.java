package io.quarkus.hibernate.reactive.transactions.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

public class HibernateReactiveTransactionsTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(Hero.class)
                    .addAsResource("initialTransactionData.sql", "import.sql")
            )
            .withConfigurationResource("application.properties");

    @Inject
    Mutiny.SessionFactory entityManager;

    @Test
    @RunOnVertxContext
    public void testReactiveManualTransaction(UniAsserter asserter) {
        Uni<Hero> hero = entityManager.withTransaction(s -> s.persist(new Hero("hero1")))
                .flatMap(v -> entityManager.withTransaction(
                        s -> s.createQuery("select h from Hero h where h.name = 'hero1'", Hero.class)
                                .getSingleResult()));

        asserter.assertThat(() -> hero, h -> assertThat("hero1").isEqualTo(h.name));

    }

    @Test
    @RunOnVertxContext
    @Transactional
    public void testReactiveAnnotationTransaction(UniAsserter asserter) {

        Long previousHeroId = 50L;

        Uni<Object> failingUpdate = entityManager.withSession(session -> {
                return updateHero(session, previousHeroId, "updatedName")
                        .flatMap(o -> updateHero(session, previousHeroId, "updatedName2"))
                        .flatMap(h -> {
                            return Uni.createFrom().failure(new RuntimeException("Failing update"));
                        }).onFailure().recoverWithNull();
            });

        Uni<Hero> refreshedHero =
                failingUpdate.flatMap(id -> entityManager.withTransaction(session -> findHero(session, previousHeroId)));

        asserter.assertThat(() -> refreshedHero, h -> {
            assertThat(h.name).isEqualTo("initialName");
        });

    }

    public Uni<Hero> updateHero(Mutiny.Session session, Long id, String newName) {
        return session.find(Hero.class, id)
                .map(h -> {
                    System.out.println("Updating hero to newName: " + newName);
                    h.setName(newName);
                    return h;
                }).call(() -> session.flush());
    }

    public Uni<Hero> findHero(Mutiny.Session session, Long id) {
        return session.find(Hero.class, id);
    }

}
