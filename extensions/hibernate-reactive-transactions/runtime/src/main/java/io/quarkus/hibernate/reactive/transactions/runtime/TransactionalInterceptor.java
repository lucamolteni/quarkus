package io.quarkus.hibernate.reactive.transactions.runtime;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.mutiny.Mutiny;

import io.smallrye.mutiny.Uni;

/**
 * An interceptor which manages reactive transactions for methods
 * annotated with {@link Transactional}.
 */
@Transactional
@Interceptor
public class TransactionalInterceptor {

    /**
     * The reactive {@link Mutiny.SessionFactory} made available by
     * the Quarkus extension for Hibernate Reactive.
     */
    @Inject
    Mutiny.SessionFactory factory;

    @Inject
    Instance<TransactionManager> transactionManager;

    /**
     * Wraps the method execution in a reactive transaction using
     * {@link Mutiny.SessionFactory#withTransaction(java.util.function.Function)}.
     */
    @AroundInvoke
    public Object withTransaction(InvocationContext invocationContext) throws Exception {
        if (transactionManager.isResolvable()) {
            TransactionManager transactionManager1 = transactionManager.getHandle().get();

            System.out.println(transactionManager1);
        }

        if (factory.getCurrentSession() == null && invocationContext.getMethod().getReturnType().equals(Uni.class)) {
            try {
                return factory.withTransaction(session -> {
                    try {
                        return (Uni<?>) invocationContext.proceed();
                    } catch (Exception e) {
                        throw new TemporaryWrapper(e);
                    }
                });
            } catch (TemporaryWrapper wrapper) {
                throw wrapper.getCause();
            }
        } else {
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
