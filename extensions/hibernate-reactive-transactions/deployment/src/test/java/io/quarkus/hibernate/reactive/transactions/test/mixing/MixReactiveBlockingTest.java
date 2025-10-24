package io.quarkus.hibernate.reactive.transactions.test.mixing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.mutiny.Mutiny;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.builder.Version;
import io.quarkus.hibernate.reactive.transactions.runtime.TransactionalInterceptor;
import io.quarkus.hibernate.reactive.transactions.test.Hero;
import io.quarkus.maven.dependency.Dependency;
import io.quarkus.runtime.BlockingOperationNotAllowedException;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

public class MixReactiveBlockingTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClasses(Hero.class, TransactionalInterceptor.class))
            .setForcedDependencies(List.of(
                    Dependency.of("io.quarkus", "quarkus-jdbc-postgresql-deployment", Version.getVersion()) // this triggers Agroal
            ));

    @Inject
    EntityManagerFactory entityManagerFactory;

    @Inject
    Mutiny.SessionFactory reactiveSessionFactory;

    @Test
    @RunOnVertxContext
    public void avoidMixingTransactionalAndWithTransactionTest(UniAsserter asserter) {
        Uni<Void> uni = avoidMixingBlockingEMWithReactiveSessionFactory();

        asserter.assertFailedWith(() -> uni,
                e -> assertThat(e.getCause())
                        .isInstanceOf(BlockingOperationNotAllowedException.class)
                        .hasMessage("Cannot start a JTA transaction from the IO thread."));
    }

    @Transactional
    public Uni<Void> avoidMixingBlockingEMWithReactiveSessionFactory() {
        Hero heroBlocking = new Hero("heroName");
        entityManagerFactory.createEntityManager().persist(heroBlocking);

        Hero heroReactive = new Hero("heroName");
        return reactiveSessionFactory.withSession(s -> s.persist(heroReactive));
    }

    @Test
    @RunOnVertxContext
    public void onlyReactiveWorks(UniAsserter asserter) {
        Uni<Hero> uni = onlyReactiveWorks();

        asserter.assertThat(() -> uni, h -> assertThat(h).isNotNull());
    }

    @Transactional
    public Uni<Hero> onlyReactiveWorks() {
        Hero heroReactive = new Hero("heroName");
        return reactiveSessionFactory.withSession(s -> s.merge(heroReactive))
                .flatMap(h -> reactiveSessionFactory.withSession(s -> s.find(Hero.class, h.id)));
    }

    @Test
    public void onlyBlockingWorks() {
        Hero h = onlyBlockingWorksOperation();

        assertThat(h).isNotNull();
    }

    @Transactional
    public Hero onlyBlockingWorksOperation() {
        Hero heroReactive = new Hero("heroName");
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        entityManager.persist(heroReactive);
        return entityManager.find(Hero.class, heroReactive.id);
    }
}
