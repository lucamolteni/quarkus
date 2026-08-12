package io.quarkus.datasource.deployment.spi;

import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.component.AvailabilityRule;

/**
 * Declares an extension can handle {@link DataSourceRequestBuildItem},
 * and provides in particular a way to check for unavailable datasources,
 * so that other extensions can check what can be requested.
 * <p>
 * Should not be consumed except by the "common" datasource extension;
 * other extensions should consume {@link DataSourceLookupBuildItem}.
 */
public final class DataSourceRequestHandlerBuildItem extends MultiBuildItem {
    private final AvailabilityRule rule;

    public DataSourceRequestHandlerBuildItem(AvailabilityRule rule) {
        this.rule = rule;
    }

    public AvailabilityRule getRule() {
        return rule;
    }
}
