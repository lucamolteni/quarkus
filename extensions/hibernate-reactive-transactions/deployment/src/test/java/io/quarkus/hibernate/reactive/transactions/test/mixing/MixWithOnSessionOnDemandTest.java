package io.quarkus.hibernate.reactive.transactions.test.mixing;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.panache.common.WithSessionOnDemand;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;

public class MixWithOnSessionOnDemandTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar.addDefaultPackage());

    @Test
    @RunOnVertxContext
    public void testTransactionalCallingSessionOnDemand(UniAsserter asserter) {
        // This should tell users do not do this and migrate to @Transactional
        asserter.assertFailedWith(
                () -> methodAnnotatedWithTransactionalCallingSessionOnDemand(),
                t -> assertThat(t)
                        .hasMessageContaining("TODO"));

        // Testing this will be all about putting a key in the context and checking if there's already one
    }

    @Transactional
    public Uni<?> methodAnnotatedWithTransactionalCallingSessionOnDemand() {
        // Do reactive stuff
        Uni<?> a = null;
        a.flatMap(b -> methodAnnotatedWithSessionOnDemand());
        return null;
    }

    @WithSessionOnDemand
    public Uni<String> methodAnnotatedWithSessionOnDemand() {
        return Uni.createFrom().item("whatever");
    }

    @Test
    @RunOnVertxContext
    public void testSessionOnDemandCallingTransactional(UniAsserter asserter) {
        // This should tell users do not do this and migrate to @Transactional
        asserter.assertFailedWith(
                () -> methodAnnotatedWithSessionOnDemandCallingTransactional(),
                t -> assertThat(t)
                        .hasMessageContaining("TODO"));
    }

    @WithSessionOnDemand
    public Uni<?> methodAnnotatedWithSessionOnDemandCallingTransactional() {
        Uni<?> a = null;
        a.flatMap(b -> methodAnnotatedWithTransactional());
        return null;
    }

    @Transactional
    public Uni<String> methodAnnotatedWithTransactional() {
        // Do reactive stuff pt 2
        return Uni.createFrom().item("whatever");
    }
}
