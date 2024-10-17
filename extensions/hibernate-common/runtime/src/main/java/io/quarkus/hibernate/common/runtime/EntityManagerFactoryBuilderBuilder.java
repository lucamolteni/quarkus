package io.quarkus.hibernate.common.runtime;

import java.util.List;
import java.util.Map;

import jakarta.persistence.PersistenceException;

import org.jboss.logging.Logger;

import io.quarkus.hibernate.common.runtime.boot.QuarkusPersistenceUnitDescriptor;

// TODO definetly need a better name
public class EntityManagerFactoryBuilderBuilder {

    private final Logger log;

    public EntityManagerFactoryBuilderBuilder(Logger log) {
        this.log = log;
    }

    @SuppressWarnings("rawtypes")
    public void verifyPropertiesPresence(Map properties) {
        if (properties != null && properties.size() != 0) {
            throw new PersistenceException(
                    "The FastbootHibernateProvider PersistenceProvider can not support runtime provided properties. "
                            + "Make sure you set all properties you need in the configuration resources before building the application.");
        }
    }

    // TODO This should return only one persistence unit instead of void
    public void validatePersistenceUnitSizeOnlyOne(String persistenceUnitName, List<?> units) {
        log.debugf("Located %s persistence units; checking each", units.size());

        if (persistenceUnitName == null && units.size() > 1) {
            // no persistence-unit name to look for was given and we found multiple
            // persistence-units
            throw new PersistenceException("No name provided and multiple persistence units found");
        }
    }

    public boolean notSameName(String persistenceUnitName, QuarkusPersistenceUnitDescriptor persistenceUnit) {
        log.debugf(
                "Checking persistence-unit [name=%s, explicit-provider=%s] against incoming persistence unit name [%s]",
                persistenceUnit.getName(), persistenceUnit.getProviderClassName(), persistenceUnitName);

        final boolean matches = persistenceUnitName == null
                || persistenceUnit.getName().equals(persistenceUnitName);
        if (!matches) {
            log.debugf("Excluding from consideration '%s' due to name mismatch", persistenceUnit.getName());
            return true;
        }
        return false;
    }

}
