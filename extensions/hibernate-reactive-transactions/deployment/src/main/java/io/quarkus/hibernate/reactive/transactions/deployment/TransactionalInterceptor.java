package io.quarkus.hibernate.reactive.transactions.deployment;

import java.lang.annotation.Annotation;

import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;

import org.hibernate.AnnotationException;
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

    /**
     * Wraps the method execution in a reactive transaction using
     * {@link Mutiny.SessionFactory#withTransaction(java.util.function.Function)}.
     */
    @AroundInvoke
    public Object withTransaction(InvocationContext invocationContext) throws Exception {

        // TODO Luca perhaps use Jandex instead of reflection to check this?
        for(Annotation a : invocationContext.getMethod().getAnnotations()) {
            if(a.toString().contains("WithSessionOnDemand")) {
                throw new AnnotationException("Cannot mix @Transactional and @WithSessionOnDemand");
            }
        }
        if (factory.getCurrentSession() == null) {
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
