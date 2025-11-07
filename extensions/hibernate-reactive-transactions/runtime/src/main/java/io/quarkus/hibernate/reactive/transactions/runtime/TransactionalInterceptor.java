package io.quarkus.hibernate.reactive.transactions.runtime;

import java.util.function.Supplier;

import io.quarkus.hibernate.reactive.runtime.HibernateReactiveRecorder;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;

import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

import static io.quarkus.hibernate.reactive.runtime.HibernateReactiveRecorder.WITH_TRANSACTION_METHOD_KEY;


/**
 * An interceptor which manages reactive transactions for methods
 * annotated with {@link Transactional}.
 */
@Transactional
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class TransactionalInterceptor {

    public static final String CURRENT_SESSION_INTERCEPTOR_KEY = "current_session_interceptor";

    private static final String ERROR_MSG = "Hibernate Reactive Panache requires a safe (isolated) Vert.x sub-context, but the current context hasn't been flagged as such.";

    @AroundInvoke
    public Object withTransaction(InvocationContext context) throws Exception {
        if (isUniReturnType(context)) {
            return withTransactionalSessionOnDemand(() -> proceedUni(context));
        }
        return context.proceed();
    }

    // TODO copied from Panache -- refactor and put in a common module?
    @SuppressWarnings("unchecked")
    protected <T> Uni<T> proceedUni(InvocationContext context) {
        try {
            return ((Uni<T>) context.proceed());
        } catch (Exception e) {
            return Uni.createFrom().failure(e);
        }
    }

    // TODO copied from Panache -- refactor and put in a common module?
    protected boolean isUniReturnType(InvocationContext context) {
        return context.getMethod().getReturnType().equals(Uni.class);
    }

    // This key is used to indicate the method was annotated with @Transactional
    // And will open a session and a transaction lazy when the first operation requrires a reactive session
    // Check HibernateReactiveRecorder.sessionSupplier to see where the session is injected
    // TODO Luca find a way to remove the duplication between this field and TransactionalInterceptor field
    public static final String TRANSACTIONAL_METHOD_KEY = "hibernate.reactive.methodTransactional";

    // This key is copied from panache and it's the marker key the WithSessionOnDemand intereceptor uses
    private static final String SESSION_ON_DEMAND_KEY = "hibernate.reactive.panache.sessionOnDemand";

    static <T> Uni<T> withTransactionalSessionOnDemand(Supplier<Uni<T>> work) {
        Context context = vertxContext();
        if(context.getLocal(SESSION_ON_DEMAND_KEY) != null) {
            return Uni.createFrom().failure(
                    new UnsupportedOperationException(
                            "Cannot call a method annotated with @Transactional from a method annotated with @WithSessionOnDemand"));
        }

        if(context.getLocal(WITH_TRANSACTION_METHOD_KEY) != null) {
            return Uni.createFrom().failure(
                    new UnsupportedOperationException(
                            "Cannot call a method annotated with @Transactional from a method annotated with @WithTransaction"));
        }

        // TODO check that there's no other session opened by session delegators for another PU

        // TODO check that there's no statelessSession opened by statelessSession delegators

        // TODO handle @Transactional#value -- first impl would be to fail for anything except REQUIRED

        // io/quarkus/hibernate/reactive/panache/common/runtime/SessionOperations.java:79
        if (context.getLocal(TRANSACTIONAL_METHOD_KEY) != null) {
            return work.get();
        } else {
            // mark this method to be @Transactional so that other Panache interceptor might fail
            context.putLocal(TRANSACTIONAL_METHOD_KEY, true);
            // perform the work and eventually close the session and remove the key
            return work.get().eventually(() -> {
                return HibernateReactiveRecorder.OPENED_SESSIONS_STATE.closeAllOpenedSessions(context);
            });
        }
    }

    /**
     *
     * @return the current vertx duplicated context
     * @throws IllegalStateException If no vertx context is found or is not a safe context as mandated by the
     *         {@link VertxContextSafetyToggle}
     */
    private static Context vertxContext() {
        Context context = Vertx.currentContext();
        if (context != null) {
            VertxContextSafetyToggle.validateContextIfExists(ERROR_MSG, ERROR_MSG);
            return context;
        } else {
            throw new IllegalStateException("No current Vertx context found");
        }
    }
}
