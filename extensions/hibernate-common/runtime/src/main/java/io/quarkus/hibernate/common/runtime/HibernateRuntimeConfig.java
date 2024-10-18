package io.quarkus.hibernate.common.runtime;

import java.util.Map;

public interface HibernateRuntimeConfig {

    Map<String, HibernateOrmRuntimeConfigPersistenceUnit> persistenceUnits();
}
