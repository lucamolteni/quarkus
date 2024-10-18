package io.quarkus.hibernate.reactive.runtime;

import java.util.Map;

import io.quarkus.hibernate.common.runtime.HibernateOrmRuntimeConfigPersistenceUnit;
import io.quarkus.hibernate.common.runtime.HibernateRuntimeConfig;
import io.quarkus.hibernate.common.runtime.PersistenceUnitUtil;
import io.quarkus.runtime.annotations.ConfigDocMapKey;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefaults;
import io.smallrye.config.WithParentName;
import io.smallrye.config.WithUnnamedKey;

@ConfigMapping(prefix = "quarkus.hibernate-reactive")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface HibernateReactiveRuntimeConfig extends HibernateRuntimeConfig {

    /**
     * Configuration for persistence units.
     */
    @WithParentName
    @WithUnnamedKey(PersistenceUnitUtil.DEFAULT_PERSISTENCE_UNIT_NAME)
    @WithDefaults
    @ConfigDocMapKey("persistence-unit-name")
    @Override
    Map<String, HibernateOrmRuntimeConfigPersistenceUnit> persistenceUnits();

    static String extensionPropertyKey(String radical) {
        return "quarkus.hibernate-reactive." + radical;
    }

    static String puPropertyKey(String puName, String radical) {
        String prefix = PersistenceUnitUtil.isDefaultPersistenceUnit(puName)
                ? "quarkus.hibernate-reactive."
                : "quarkus.hibernate-reactive.\"" + puName + "\".";
        return prefix + radical;
    }
}
