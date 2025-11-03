package io.quarkus.hibernate.reactive.transactions.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.impl.ComputingCache;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;

import org.hibernate.reactive.common.spi.Implementor;
import org.hibernate.reactive.context.impl.BaseKey;
import org.hibernate.reactive.mutiny.Mutiny;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME;

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

    // This key is used to indicate that reactive sessions should be opened lazily/on-demand (when needed) in the current vertx context
    private static final String SESSION_ON_DEMAND_KEY = "hibernate.reactive.panache.sessionOnDemand";

    // This key is used to keep track of the Set<String> sessions created on demand
    private static final String SESSION_ON_DEMAND_OPENED_KEY = "hibernate.reactive.panache.sessionOnDemandOpened";


    static <T> Uni<T> withTransactionalSessionOnDemand(Supplier<Uni<T>> work) {
        // TODO register that we're in @Transactional so that @WithSessionOnDemand can detect it and fail

        // TODO register that we're in @Transactional so that session delegators can detect it and create a session if necessary

        // TODO check that there's no other session opened by session delegators for another PU

        // TODO check that there's no statelessSession opened by statelessSession delegators

        // TODO on exception thrown by "work", check if it's a rollback transaction (see @Transaction.rollbackOn)
        //   and if so, trigger the rollback
        // /org/hibernate/reactive/mutiny/impl/MutinySessionImpl.java:511

        // TODO handle @Transactional#value -- first impl would be to fail for anything except REQUIRED

        // io/quarkus/hibernate/reactive/panache/common/runtime/SessionOperations.java:79
        Context context = vertxContext();
        if (context.getLocal(SESSION_ON_DEMAND_KEY) != null) {
            // context already marked - no need to set the key and close the session
            return work.get();
        } else {
            // mark the lazy session
            context.putLocal(SESSION_ON_DEMAND_KEY, true);
            // perform the work and eventually close the session and remove the key
            return work.get().eventually(() -> {
                context.removeLocal(SESSION_ON_DEMAND_KEY);
                Set<String> onDemandSessionCreated = context.getLocal(SESSION_ON_DEMAND_OPENED_KEY);
                // Close only the sessions that have been created lazily (onDemand) in withSession
                // See this.getSession(String persistenceUnitName)
                if (onDemandSessionCreated != null) {
                    List<Uni<Void>> closedSessions = new ArrayList<>();
                    for (String s : onDemandSessionCreated) {
                        closedSessions.add(closeSession(s));
                    }
                    context.removeLocal(SESSION_ON_DEMAND_OPENED_KEY);
                    return Uni.combine().all().unis(closedSessions).discardItems();
                } else {
                    return Uni.createFrom().voidItem();
                }
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

    static Uni<Void> closeSession(String persistenceUnitName) {
        Context context = vertxContext();
        org.hibernate.reactive.context.Context.Key<Mutiny.Session> key = SESSION_KEY_MAP.getValue(persistenceUnitName);
        Mutiny.Session current = context.getLocal(key);
        if (current != null && current.isOpen()) {
            return current.close().eventually(() -> context.removeLocal(key));
        }
        return Uni.createFrom().voidItem();
    }

    private static final ComputingCache<String, org.hibernate.reactive.context.Context.Key<Mutiny.Session>> SESSION_KEY_MAP = new ComputingCache<>(
            k -> createSessionKey(k));

    private static final ComputingCache<String, Mutiny.SessionFactory> SESSION_FACTORY_MAP = new ComputingCache<>(
            k -> createSessionFactory(k));

    private static Mutiny.SessionFactory createSessionFactory(String persistenceunitname) {
        Mutiny.SessionFactory sessionFactory;

        // Note that Mutiny.SessionFactory is @ApplicationScoped bean - it's safe to use the cached client proxy
        if (DEFAULT_PERSISTENCE_UNIT_NAME.equals(persistenceunitname)) {
            sessionFactory = Arc.container().instance(Mutiny.SessionFactory.class).get();
        } else {
            sessionFactory = Arc.container().instance(Mutiny.SessionFactory.class,
                    new PersistenceUnit.PersistenceUnitLiteral(persistenceunitname)).get();
        }

        if (sessionFactory == null) {
            throw new IllegalStateException("Mutiny.SessionFactory bean not found");
        }
        return sessionFactory;
    }

    private static org.hibernate.reactive.context.Context.Key<Mutiny.Session> createSessionKey(String persistenceUnitName) {
        Implementor implementor = (Implementor) ClientProxy
                .unwrap(SESSION_FACTORY_MAP.getValue(persistenceUnitName));
        return new BaseKey<>(Mutiny.Session.class, implementor.getUuid());
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
