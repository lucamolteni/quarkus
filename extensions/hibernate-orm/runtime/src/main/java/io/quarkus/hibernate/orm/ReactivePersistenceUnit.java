package io.quarkus.hibernate.orm;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PACKAGE;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.inject.Qualifier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

/**
 * This annotation has two different purposes.
 * It is a qualifier used to specify to which persistence unit the injected {@link EntityManagerFactory} or
 * {@link EntityManager} belongs.
 * <p>
 * This allows for regular CDI bean injection of both interfaces.
 * <p>
 * It is also used to mark packages as part of a given persistence unit.
 */
@Target({ TYPE, FIELD, METHOD, PARAMETER, PACKAGE })
@Retention(RUNTIME)
@Documented
@Qualifier
public @interface ReactivePersistenceUnit {
}
