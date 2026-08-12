package io.quarkus.hibernate.orm.deployment;

import static io.quarkus.deployment.component.ComponentLookup.AVAILABLE;
import static io.quarkus.deployment.component.ComponentLookup.unavailable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jboss.logging.Logger;

import io.quarkus.datasource.deployment.spi.DataSourceLookupBuildItem;
import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.component.AvailabilityRule;
import io.quarkus.deployment.component.ComponentLookup;
import io.quarkus.hibernate.orm.deployment.spi.AdditionalPersistenceUnitBuildItem;
import io.quarkus.hibernate.orm.deployment.spi.PersistenceUnitLookupBuildItem;
import io.quarkus.hibernate.orm.deployment.util.HibernateProcessorUtil;
import io.quarkus.hibernate.orm.runtime.HibernateOrmRuntimeConfig;
import io.quarkus.hibernate.orm.runtime.migration.MultiTenancyStrategy;
import io.quarkus.runtime.util.ProgrammingParadigm;
import io.quarkus.runtime.util.Reason;

class HibernateOrmLookupProcessor {

    private static final Logger LOG = Logger.getLogger(HibernateOrmLookupProcessor.class);

    @BuildStep
    PersistenceUnitLookupBuildItem defineLookup(HibernateOrmConfig config,
            Capabilities capabilities,
            List<AdditionalPersistenceUnitBuildItem> additionalPersistenceUnits,
            DataSourceLookupBuildItem dataSourceLookupBuildItem) {
        var lookup = new ComponentLookup();

        checkBlockingEnabled(lookup, config);
        checkJdbcEnabled(lookup, config);
        checkReactivePresent(lookup, capabilities);
        checkReactiveEnabled(lookup, config);
        checkDataSourceAvailable(lookup, config, additionalPersistenceUnits, dataSourceLookupBuildItem.getLookup());
        checkReactiveDataSourceDefined(lookup, config, additionalPersistenceUnits);
        checkBlockingDataSourceDefined(lookup, config, additionalPersistenceUnits);

        return new PersistenceUnitLookupBuildItem(lookup);
    }

    private void checkBlockingEnabled(ComponentLookup lookup, HibernateOrmConfig config) {
        var blockingEnabled = config.blocking();
        if (!blockingEnabled) {
            LOG.infof("Hibernate ORM was disabled explicitly by quarkus.hibernate-orm.blocking=false");
        }
        lookup.checkAvailability(new AvailabilityRule(ProgrammingParadigm.BLOCKING, name -> {
            if (!blockingEnabled) {
                return unavailable(new Reason(String.format(Locale.ROOT,
                        "Hibernate ORM was disabled explicitly by setting '%s' to 'false'",
                        HibernateOrmRuntimeConfig.puPropertyKey(name, "blocking"))));
            }
            return AVAILABLE;
        }));
    }

    private void checkJdbcEnabled(ComponentLookup lookup, HibernateOrmConfig config) {
        lookup.checkAvailability(new AvailabilityRule(ProgrammingParadigm.BLOCKING, name -> {
            if (!config.persistenceUnits().get(name).jdbc().enabled().orElse(true)) {
                return unavailable(new Reason(String.format(Locale.ROOT,
                        "Hibernate ORM was disabled explicitly by setting '%s' to 'false'",
                        HibernateOrmRuntimeConfig.puPropertyKey(name, "jdbc.enabled"))));
            }
            return AVAILABLE;
        }));
    }

    private void checkReactivePresent(ComponentLookup lookup, Capabilities capabilities) {
        var hibernateReactivePresent = capabilities.isPresent(Capability.HIBERNATE_REACTIVE);
        lookup.checkAvailability(new AvailabilityRule(ProgrammingParadigm.REACTIVE, name -> {
            if (!hibernateReactivePresent) {
                return unavailable(new Reason("Hibernate Reactive extension is absent"));
            }
            return AVAILABLE;
        }));
    }

    private void checkReactiveEnabled(ComponentLookup lookup, HibernateOrmConfig config) {
        lookup.checkAvailability(new AvailabilityRule(ProgrammingParadigm.REACTIVE, name -> {
            if (!config.persistenceUnits().get(name).reactive().enabled().orElse(true)) {
                return unavailable(new Reason(String.format(Locale.ROOT,
                        "Hibernate Reactive was disabled explicitly by setting '%s' to 'false'",
                        HibernateOrmRuntimeConfig.puPropertyKey(name, "reactive.enabled"))));
            }
            return AVAILABLE;
        }));
    }

    private void checkDataSourceAvailable(ComponentLookup lookup, HibernateOrmConfig config,
            List<AdditionalPersistenceUnitBuildItem> additionalPersistenceUnits,
            ComponentLookup dataSourceLookup) {
        for (var paradigm : ProgrammingParadigm.values()) {
            String label = switch (paradigm) {
                case BLOCKING -> "JDBC";
                case REACTIVE -> "Reactive";
            };
            lookup.checkAvailability(new AvailabilityRule(paradigm, name -> {
                Optional<String> dataSourceName = resolveDataSourceName(additionalPersistenceUnits, config, name);
                if (dataSourceName.isPresent()) {
                    List<Reason> dataSourceUnavailableReason = dataSourceLookup.unavailableReasons(
                            dataSourceName.get(), paradigm);
                    if (!dataSourceUnavailableReason.isEmpty()) {
                        return unavailable(new Reason(
                                String.format(Locale.ROOT, "%s datasource '%s' cannot be created",
                                        label, dataSourceName.get()),
                                dataSourceUnavailableReason));
                    }
                }
                return AVAILABLE;
            }));
        }
    }

    private void checkReactiveDataSourceDefined(ComponentLookup lookup, HibernateOrmConfig config,
            List<AdditionalPersistenceUnitBuildItem> additionalPersistenceUnits) {
        // Reactive does not support multitenancy so we always require a datasource
        // See https://github.com/quarkusio/quarkus/issues/15959
        lookup.checkAvailability(new AvailabilityRule(ProgrammingParadigm.REACTIVE, name -> {
            Optional<String> dataSourceName = resolveDataSourceName(additionalPersistenceUnits, config, name);
            if (dataSourceName.isEmpty()) {
                String dsConfigProperty = HibernateOrmRuntimeConfig.puPropertyKey(name, "datasource");
                return unavailable(new Reason(String.format(Locale.ROOT,
                        "Datasource must be defined for persistence unit '%s'. "
                                + "Set the datasource via the '%s' property. "
                                + "Refer to https://quarkus.io/guides/datasource for guidance.",
                        name, dsConfigProperty)));
            }
            return AVAILABLE;
        }));
    }

    private void checkBlockingDataSourceDefined(ComponentLookup lookup, HibernateOrmConfig config,
            List<AdditionalPersistenceUnitBuildItem> additionalPersistenceUnits) {
        lookup.checkAvailability(new AvailabilityRule(ProgrammingParadigm.BLOCKING, name -> {
            Optional<String> dataSourceName = resolveDataSourceName(additionalPersistenceUnits, config, name);
            if (dataSourceName.isEmpty()) {
                MultiTenancyStrategy multiTenancyStrategy = MultiTenancyStrategy
                        .valueOf(config.persistenceUnits().get(name).multitenant()
                                .orElse(MultiTenancyStrategy.NONE.name()).toUpperCase(Locale.ROOT));
                if (multiTenancyStrategy != MultiTenancyStrategy.DATABASE) {
                    String dsConfigProperty = HibernateOrmRuntimeConfig.puPropertyKey(name, "datasource");
                    return unavailable(new Reason(String.format(Locale.ROOT,
                            "Datasource must be defined for persistence unit '%s'. "
                                    + "Set the datasource via the '%s' property. "
                                    + "Alternatively, for dynamic datasource selection, set '%s=database'. "
                                    + "Refer to https://quarkus.io/guides/datasource "
                                    + "or https://quarkus.io/guides/hibernate-orm#database-approach "
                                    + "for guidance.",
                            name, dsConfigProperty, dsConfigProperty)));
                }
            }
            return AVAILABLE;
        }));
    }

    private static Optional<String> resolveDataSourceName(
            List<AdditionalPersistenceUnitBuildItem> additionalPersistenceUnits,
            HibernateOrmConfig config, String puName) {
        return additionalPersistenceUnits.stream()
                .filter(item -> item.getPersistenceUnitName().equals(puName))
                .findFirst()
                .flatMap(AdditionalPersistenceUnitBuildItem::getDataSourceName)
                .or(() -> HibernateProcessorUtil.getDataSourceName(config, puName));
    }

}
