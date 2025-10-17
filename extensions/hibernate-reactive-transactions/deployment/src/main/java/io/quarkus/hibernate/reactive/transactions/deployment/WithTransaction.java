package io.quarkus.hibernate.reactive.transactions.deployment;

import jakarta.interceptor.InterceptorBinding;
import org.hibernate.reactive.mutiny.Mutiny;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Indicates that the annotated operation or managed bean
 * requires access to a CDI request-scoped reactive
 * {@linkplain Mutiny.StatelessSession stateless session}.
 */
@InterceptorBinding
@Target({ TYPE, METHOD })
@Retention(RUNTIME)
public @interface WithTransaction {
}