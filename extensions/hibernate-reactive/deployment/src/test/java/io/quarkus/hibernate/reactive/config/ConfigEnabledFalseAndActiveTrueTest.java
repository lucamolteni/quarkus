package io.quarkus.hibernate.reactive.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.test.QuarkusUnitTest;

@Disabled("TODO     Luca I think these tests are not necessary anymore after fixing https://github.com/quarkusio/quarkus/issues/13425")
public class ConfigEnabledFalseAndActiveTrueTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(MyEntity.class))
            .withConfigurationResource("application.properties")
            .overrideConfigKey("quarkus.hibernate-reactive.enabled", "false")
            .overrideConfigKey("quarkus.hibernate-reactive.active", "true")
            .assertException(throwable -> assertThat(throwable)
                    .isInstanceOf(ConfigurationException.class)
                    .hasMessageContaining(
                            "Hibernate ORM activated explicitly for persistence unit '<default>', but the Hibernate ORM extension was disabled at build time",
                            "If you want Hibernate ORM to be active for this persistence unit, you must set 'quarkus.hibernate-reactive.enabled' to 'true' at build time",
                            "If you don't want Hibernate ORM to be active for this persistence unit, you must leave 'quarkus.hibernate-reactive.active' unset or set it to 'false'"));

    @Test
    public void test() {
        // Startup will fail
    }
}
