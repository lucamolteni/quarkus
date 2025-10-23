package io.quarkus.hibernate.reactive.transactions.deployment;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;

class HibernateReactiveTransactionsProcessor {

    private static final String FEATURE = "hibernate-reactive-transactions";

    private static final DotName TRANSACTIONAL = DotName.createSimple(Transactional.class.getName());

    private static final String WITH_SESSION_ON_DEMAND = "io.quarkus.hibernate.reactive.panache.common.WithSessionOnDemand";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @Inject
    CombinedIndexBuildItem combinedIndexBuildItem;

    @BuildStep
    void register(
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass // TOOD Luca hack to make this build step run
    ) {

        IndexView index = combinedIndexBuildItem.getIndex();

        for (AnnotationInstance deserializeInstance : index.getAnnotations(TRANSACTIONAL)) {
            AnnotationTarget annotationTarget = deserializeInstance.target();

            if (annotationTarget.hasAnnotation(WITH_SESSION_ON_DEMAND)) {
                throw new ConfigurationException("Cannot mix @Transactional and @WithSessionOnDemand");
            }

        }

    }
}
