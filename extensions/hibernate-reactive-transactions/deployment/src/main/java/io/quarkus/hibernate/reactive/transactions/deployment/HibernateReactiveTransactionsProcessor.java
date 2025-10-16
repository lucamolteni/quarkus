package io.quarkus.hibernate.reactive.transactions.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

class HibernateReactiveTransactionsProcessor {

    private static final String FEATURE = "hibernate-reactive-transactions";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
