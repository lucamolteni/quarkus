package io.quarkus.hibernate.reactive.transactions.test;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.inject.Inject;

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
                    .addClasses(Hero.class))
            .withConfigurationResource("application.properties");

    @Inject
    Mutiny.SessionFactory entityManager;

    @Test
    @RunOnVertxContext
    public void testReactive(UniAsserter asserter) {
        Uni<Hero> hero = entityManager.withTransaction(s -> s.persist(new Hero("hero1")))
                .flatMap(v -> entityManager.withTransaction(
                        s -> s.createQuery("select h from Hero h where h.name = 'hero1'", Hero.class)
                                .getSingleResult()));

        asserter.assertThat(() -> hero, h -> assertThat("hero1").isEqualTo(h.name));

    }

}
