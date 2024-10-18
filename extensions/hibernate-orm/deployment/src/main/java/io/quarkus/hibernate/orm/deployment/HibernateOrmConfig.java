package io.quarkus.hibernate.orm.deployment;

import io.quarkus.hibernate.common.deployment.HibernateConfig;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.hibernate-orm")
@ConfigRoot
public interface HibernateOrmConfig extends HibernateConfig {
}
