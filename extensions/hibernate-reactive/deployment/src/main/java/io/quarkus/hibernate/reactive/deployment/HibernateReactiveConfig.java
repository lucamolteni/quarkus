package io.quarkus.hibernate.reactive.deployment;

import io.quarkus.hibernate.common.deployment.HibernateConfig;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "quarkus.hibernate-reactive")
@ConfigRoot
public interface HibernateReactiveConfig extends HibernateConfig {
}
