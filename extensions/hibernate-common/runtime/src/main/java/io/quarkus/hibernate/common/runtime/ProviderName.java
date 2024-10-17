package io.quarkus.hibernate.common.runtime;

import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.jpa.boot.spi.PersistenceUnitDescriptor;
import org.jboss.logging.Logger;

public class ProviderName {

    public ProviderName(Logger log) {
        this.log = log;
    }

    private Logger log;

    @SuppressWarnings("rawtypes")
    public String extractRequestedProviderName(PersistenceUnitDescriptor persistenceUnit,
            Map integration, Class<?> currentProvider) {
        final String integrationProviderName = extractProviderName(integration);
        if (integrationProviderName != null) {
            log.debugf("Integration provided explicit PersistenceProvider [%s]", integrationProviderName);
            return integrationProviderName;
        }

        final String persistenceUnitRequestedProvider = extractProviderName(persistenceUnit);
        if (persistenceUnitRequestedProvider != null) {
            log.debugf("Persistence-unit [%s] requested PersistenceProvider [%s]", persistenceUnit.getName(),
                    persistenceUnitRequestedProvider);
            return persistenceUnitRequestedProvider;
        }

        // NOTE : if no provider requested we assume we are the provider (the calls got
        // to us somehow...)
        log.debug("No PersistenceProvider explicitly requested, assuming Hibernate");
        return currentProvider.getName();
    }

    @SuppressWarnings("rawtypes")
    private static String extractProviderName(Map integration) {
        if (integration == null) {
            return null;
        }
        final String setting = (String) integration.get(AvailableSettings.JAKARTA_PERSISTENCE_PROVIDER);
        return setting == null ? null : setting.trim();
    }

    private static String extractProviderName(PersistenceUnitDescriptor persistenceUnit) {
        final String persistenceUnitRequestedProvider = persistenceUnit.getProviderClassName();
        return persistenceUnitRequestedProvider == null ? null : persistenceUnitRequestedProvider.trim();
    }
}
