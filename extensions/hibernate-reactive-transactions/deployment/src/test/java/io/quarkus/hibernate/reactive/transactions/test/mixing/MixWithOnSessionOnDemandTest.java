package io.quarkus.hibernate.reactive.transactions.test.mixing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.transaction.Transactional;

import org.hibernate.AnnotationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.panache.common.WithSessionOnDemand;
import io.quarkus.hibernate.reactive.panache.common.runtime.AbstractUniInterceptor;
import io.quarkus.hibernate.reactive.panache.common.runtime.SessionOperations;
import io.quarkus.hibernate.reactive.panache.common.runtime.WithSessionOnDemandInterceptor;
import io.quarkus.hibernate.reactive.transactions.deployment.TransactionalInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;

public class MixWithOnSessionOnDemandTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(
                            TransactionalInterceptor.class,
                            Transactional.class,
                            AbstractUniInterceptor.class,
                            WithSessionOnDemandInterceptor.class,
                            WithSessionOnDemand.class,
                            SessionOperations.class
                    ));

    @Test
    @RunOnVertxContext
    public void avoidMixingTransactionalAnnotationsTest() {
        assertThatThrownBy(() -> avoidMixingTransactionalAnnotations())
                .isInstanceOf(AnnotationException.class)
                .hasMessage("Cannot mix @Transactional and @WithSessionOnDemand");
    }

    @Transactional
    @WithSessionOnDemand
    public Uni<?> avoidMixingTransactionalAnnotations() {
        throw new UnsupportedOperationException("this shouldn't be called");
    }
}
