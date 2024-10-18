package io.quarkus.hibernate.reactive.deployment;

import java.util.function.BooleanSupplier;

/**
 * Supplier that can be used to only run build steps
 * if the Hibernate ORM extension is enabled.
 */
// TODO Ideally we'd rely on separate configuration for Hibernate Reactive,
//  both in general and specifically to enable/disable the extension.
//  But we would first need to split common code to a separate artifact,
//  and that's a lot of work that will conflict with other ongoing changes,
//  so we better wait.
//  See also https://github.com/quarkusio/quarkus/issues/13425
// TODO Luca remove this
public class HibernateReactiveEnabled implements BooleanSupplier {

    private final HibernateReactiveConfig config;

    HibernateReactiveEnabled(HibernateReactiveConfig config) {
        this.config = config;
    }

    @Override
    public boolean getAsBoolean() {
        return config.enabled();
    }

}
