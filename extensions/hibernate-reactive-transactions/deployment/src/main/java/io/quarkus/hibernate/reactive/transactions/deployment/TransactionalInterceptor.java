package io.quarkus.hibernate.reactive.transactions.deployment;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;
import org.hibernate.reactive.mutiny.Mutiny;

/**
 * An interceptor which manages the lifecycle of a CDI request-scoped
 * reactive {@linkplain Mutiny.StatelessSession stateless session}.
 */
@Transactional
@Interceptor
public class TransactionalInterceptor {

    /**
     * The reactive {@link Mutiny.SessionFactory} made available by
     * the Quarkus extension for Hibernate Reactive.
     */
    @Inject Mutiny.SessionFactory factory;

    /**
     * If the operation is a reactive operation, that is, if it returns
     * {@link Uni}, associate a stateless session with the stream. The
     * stateless session will be automatically cleaned up when the
     * {@code Uni} returned by the operation terminates.
     */
    @AroundInvoke
    public Object withSession(InvocationContext invocationContext) throws Exception {
        if ( factory.getCurrentSession() == null) {
            try {
                return factory.withTransaction(session -> {
                    try {
                        return (Uni<?>) invocationContext.proceed();
                    }
                    catch (Exception e) {
                        throw new TemporaryWrapper(e);
                    }
                });
            }
            catch (TemporaryWrapper wrapper) {
                throw wrapper.getCause();
            }
        }
        else {
            return invocationContext.proceed();
        }
    }

    private static class TemporaryWrapper extends RuntimeException {
        private TemporaryWrapper(Exception cause) {
            super(cause);
        }

        @Override
        public Exception getCause() {
            return (Exception) super.getCause();
        }
    }
}