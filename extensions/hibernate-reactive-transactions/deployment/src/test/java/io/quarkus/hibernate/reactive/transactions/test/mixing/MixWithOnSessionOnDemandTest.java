package io.quarkus.hibernate.reactive.transactions.test.mixing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.panache.common.WithSessionOnDemand;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;

public class MixWithOnSessionOnDemandTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar.addDefaultPackage())
            .assertException(throwable -> assertThat(throwable)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(
                            "Cannot mix @Transactional and @WithSessionOnDemand"));

    @Test
    @RunOnVertxContext
    public void avoidMixingTransactionalAnnotationsTest() {
        fail(); // this will never be called, extension will fail seeing the method below
    }

    @Test
    @RunOnVertxContext
    public void actualTest() {
        fail(); // this will never be called, extension will fail seeing the method below
    }

    @Test
    @RunOnVertxContext
    public void actualTest() {
        // This should tell users do not do this and migrate to @Transactional
        avoidMixingTransactionalAnnotations1();
    }

    @Transactional
    public Uni<?> avoidMixingTransactionalAnnotations1() {
        // Do reactive stuff
        Uni<?> a = null;
        a.flatMap(a -> avoidMixingTransactionalAnnotations2());
        return null;
    }

    @WithSessionOnDemand
    public Uni<?> avoidMixingTransactionalAnnotations2() {
        // Do reactive stuff pt 2
        return null;
    }

    @Transactional
    @WithSessionOnDemand
    public Uni<?> avoidMixingTransactionalAnnotations() {
        throw new UnsupportedOperationException("this shouldn't be called");
    }
}
