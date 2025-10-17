package io.quarkus.hibernate.reactive.transactions.test.mixing;

import jakarta.transaction.Transactional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.hibernate.reactive.panache.common.WithSessionOnDemand;
import io.quarkus.hibernate.reactive.panache.common.runtime.AbstractUniInterceptor;
import io.quarkus.hibernate.reactive.panache.common.runtime.WithSessionOnDemandInterceptor;
import io.quarkus.hibernate.reactive.transactions.deployment.TransactionalInterceptor;
import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.smallrye.mutiny.Uni;

public class MixWithOnSessionOnDemandTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(TransactionalInterceptor.class, AbstractUniInterceptor.class,
                            WithSessionOnDemandInterceptor.class))
            .withConfigurationResource("application.properties");

    @Test
    @RunOnVertxContext
    public void avoidMixingTransactionalAnnotationsTest() {
        // TODO Luca test is running (so this is not failing) but
        //  - TransactionalInterceptor is running fine (should fail)
        //  - WithSessionOnDemandInterceptor is not being called (it should fail anyway)
        avoidMixingTransactionalAnnotations();
    }

    @Transactional
    @WithSessionOnDemand
    public Uni avoidMixingTransactionalAnnotations() {
        System.out.println("++++ here?");
        throw new UnsupportedOperationException();
    }

}
