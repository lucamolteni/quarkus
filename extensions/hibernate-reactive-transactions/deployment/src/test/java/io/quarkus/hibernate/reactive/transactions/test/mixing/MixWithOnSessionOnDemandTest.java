package io.quarkus.hibernate.reactive.transactions.test.mixing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.panache.common.WithSessionOnDemand;
import io.quarkus.hibernate.reactive.panache.common.runtime.AbstractUniInterceptor;
import io.quarkus.hibernate.reactive.panache.common.runtime.SessionOperations;
import io.quarkus.hibernate.reactive.panache.common.runtime.WithSessionOnDemandInterceptor;
import io.quarkus.hibernate.reactive.transactions.runtime.TransactionalInterceptor;
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

    @Transactional
    @WithSessionOnDemand
    public Uni<?> avoidMixingTransactionalAnnotations() {
        throw new UnsupportedOperationException("this shouldn't be called");
    }
}
