package io.quarkus.hibernate.reactive.runtime;

import static io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import io.smallrye.mutiny.Uni;
import org.hibernate.SessionFactory;
import org.hibernate.reactive.common.spi.Implementor;
import org.hibernate.reactive.context.impl.BaseKey;
import org.hibernate.reactive.mutiny.Mutiny;
import org.hibernate.reactive.mutiny.delegation.MutinySessionDelegator;
import org.hibernate.reactive.mutiny.delegation.MutinyStatelessSessionDelegator;

import io.quarkus.arc.ActiveResult;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ClientProxy;
import io.quarkus.arc.SyntheticCreationalContext;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.hibernate.orm.runtime.HibernateOrmRuntimeConfig;
import io.quarkus.hibernate.orm.runtime.JPAConfig;
import io.quarkus.hibernate.orm.runtime.PersistenceUnitUtil;
import io.quarkus.hibernate.orm.runtime.integration.HibernateOrmIntegrationRuntimeDescriptor;
import io.quarkus.reactive.datasource.runtime.ReactiveDataSourceUtil;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import org.hibernate.reactive.mutiny.impl.MutinySessionImpl;

@Recorder
public class HibernateReactiveRecorder {
    private final RuntimeValue<HibernateOrmRuntimeConfig> runtimeConfig;

    public HibernateReactiveRecorder(final RuntimeValue<HibernateOrmRuntimeConfig> runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
    }

    /**
     * The feature needs to be initialized, even if it's not enabled.
     *
     * @param enabled Set to false if it's not being enabled, to log appropriately.
     */
    public void callHibernateReactiveFeatureInit(boolean enabled) {
        HibernateReactive.featureInit(enabled);
    }

    public void initializePersistenceProvider(
            Map<String, List<HibernateOrmIntegrationRuntimeDescriptor>> integrationRuntimeDescriptors) {
        ReactivePersistenceProviderSetup.registerRuntimePersistenceProvider(runtimeConfig.getValue(),
                integrationRuntimeDescriptors);
    }

    public Supplier<ActiveResult> checkActiveSupplier(String puName, Optional<String> dataSourceName,
            Set<String> entityClassNames) {
        return new Supplier<>() {
            @Override
            public ActiveResult get() {
                Optional<Boolean> active = runtimeConfig.getValue().persistenceUnits().get(puName).active();
                if (active.isPresent() && !active.get()) {
                    return ActiveResult.inactive(
                            PersistenceUnitUtil.persistenceUnitInactiveReasonDeactivated(puName, dataSourceName));
                }

                if (entityClassNames.isEmpty() && dataSourceName.isPresent()) {
                    // Persistence units are inactive when the corresponding datasource is inactive.
                    var dataSourceBean = ReactiveDataSourceUtil.dataSourceInstance(dataSourceName.get()).getHandle().getBean();
                    var dataSourceActive = dataSourceBean.checkActive();
                    if (!dataSourceActive.value()) {
                        return ActiveResult.inactive(
                                String.format(Locale.ROOT,
                                        "Persistence unit '%s' was deactivated automatically because it doesn't include any entity type and its datasource '%s' was deactivated.",
                                        puName,
                                        dataSourceName.get()),
                                dataSourceActive);
                    }
                }

                return ActiveResult.active();
            }
        };
    }

    public Function<SyntheticCreationalContext<Mutiny.SessionFactory>, Mutiny.SessionFactory> mutinySessionFactory(
            String persistenceUnitName) {
        return new Function<SyntheticCreationalContext<Mutiny.SessionFactory>, Mutiny.SessionFactory>() {
            @Override
            public Mutiny.SessionFactory apply(SyntheticCreationalContext<Mutiny.SessionFactory> context) {
                JPAConfig jpaConfig = context.getInjectedReference(JPAConfig.class);

                SessionFactory sessionFactory = jpaConfig
                        .getEntityManagerFactory(persistenceUnitName, true)
                        .unwrap(SessionFactory.class);

                return sessionFactory.unwrap(Mutiny.SessionFactory.class);
            }
        };
    }

    public Function<SyntheticCreationalContext<Mutiny.Session>, Mutiny.Session> sessionSupplier(String persistenceUnitName) {
        return new Function<SyntheticCreationalContext<Mutiny.Session>, Mutiny.Session>() {

            @Override
            public Mutiny.Session apply(SyntheticCreationalContext<Mutiny.Session> context) {
                return new MutinySessionDelegator() {
                    @Override
                    public Mutiny.Session delegate() {

                        // TODO check we're in @Transactional
                        // TODO get the session from vert.x context or open it (similar to Panache.getSession)
                        // To open, use SessionFactory#openSessionWithLazyConnectionOpening -> returns Mutiny.Session

                        return getSession(persistenceUnitName);
                    }
                };
            }
        };
    }

    // This key is used to indicate that reactive transaction should be opened lazily/on-demand (when needed) in the current vertx context
    public static final String TRANSACTION_ON_DEMAND_KEY = "hibernate.reactive.panache.transactionOnDemand";

    // This key is used to keep track of the Set<String> sessions created on demand
    private static final String TRANSACTION_ON_DEMAND_OPENED_KEY = "hibernate.reactive.panache.transactionOnDemandOpened";

    public static Mutiny.Session getSession(String persistenceUnitName) {
        Context context = Vertx.currentContext();
        org.hibernate.reactive.context.Context.Key<Mutiny.Session> key = createSessionKey(persistenceUnitName);
        Mutiny.Session current = context.getLocal(key);
        if (current != null && current.isOpen()) {
            // reuse the existing reactive session
            return current;
        } else {
            if (context.getLocal(TRANSACTION_ON_DEMAND_KEY) != null) {
                // This will keep track of all on-demand opened sessions
                Set<String> onDemandSessionsCreated = context.getLocal(TRANSACTION_ON_DEMAND_OPENED_KEY);
                if (onDemandSessionsCreated == null) {
                    onDemandSessionsCreated = new HashSet<>();
                    context.putLocal(TRANSACTION_ON_DEMAND_OPENED_KEY, onDemandSessionsCreated);
                }

                if (onDemandSessionsCreated.contains(persistenceUnitName)) {
                    // a new reactive session is opened in a previous stage, reuse it
                    return getCurrentSession(persistenceUnitName);
                } else {
                    // open a new reactive session and store it in the vertx duplicated context
                    // the context was marked as "lazy" which means that the session will be eventually closed
                    onDemandSessionsCreated.add(persistenceUnitName);
                    Mutiny.SessionFactory sessionFactory = createSessionFactory(persistenceUnitName);

                    // To open, use SessionFactory#openSessionWithLazyConnectionOpening -> returns Mutiny.Session
                    // createSessionInSnapshot

                    // This is replaced by the TRANSACTION_ON_DEMAND_KEY inside the intereceptor
//                    context.putLocal("createTransaction", true);
                    MutinySessionImpl session = (MutinySessionImpl) sessionFactory.createSession();

                    context.putLocal(key, session);



                    return session;
                }
            } else {
                throw new IllegalStateException("No current Mutiny.Session found"
                        + "\n\t- no reactive session was found in the Vert.x context and the context was not marked to open a new session lazily"
                        + "\n\t- a session is opened automatically for JAX-RS resource methods annotated with an HTTP method (@GET, @POST, etc.); inherited annotations are not taken into account"
                        + "\n\t- you may need to annotate the business method with @WithSession or @WithTransaction");
            }
        }
    }

    public static Mutiny.Session getCurrentSession(String persistenceUnitName) {
        Context context = Vertx.currentContext();
        Mutiny.Session current = context.getLocal(createSessionKey(persistenceUnitName));
        if (current != null && current.isOpen()) {
            return current;
        }
        return null;
    }

    public static org.hibernate.reactive.context.Context.Key<Mutiny.Session> createSessionKey(String persistenceUnitName) {
        Mutiny.SessionFactory value = createSessionFactory(persistenceUnitName);
        Implementor implementor = (Implementor) ClientProxy
                .unwrap(value);
        return new BaseKey<>(Mutiny.Session.class, implementor.getUuid());
    }

    public static Mutiny.SessionFactory createSessionFactory(String persistenceunitname) {
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

    public Function<SyntheticCreationalContext<Mutiny.StatelessSession>, Mutiny.StatelessSession> statelessSessionSupplier(
            String persistenceUnitName) {
        return new Function<SyntheticCreationalContext<Mutiny.StatelessSession>, Mutiny.StatelessSession>() {

            @Override
            public Mutiny.StatelessSession apply(SyntheticCreationalContext<Mutiny.StatelessSession> context) {
                return new MutinyStatelessSessionDelegator() {
                    @Override
                    public Mutiny.StatelessSession delegate() {
                        // TODO check we're in @Transactional
                        // TODO get the session from vert.x context or open it (similar to Panache.getSession)
                        // To open, use SessionFactory#openSessionWithLazyConnectionOpening -> returns Mutiny.Session

                        throw new UnsupportedOperationException();

                    }
                };
            }
        };
    }

}
