package io.quarkus.hibernate.reactive.transactions.runtime;

import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transactional;

import java.util.Optional;


@Transactional(Transactional.TxType.REQUIRED)
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 300)
public class TransactionalInterceptorRequired extends TransactionalInterceptorBase{

    @Override
    @AroundInvoke
    public Object intercept(InvocationContext context) throws Exception {
        return super.intercept(context);
    }

    @Override
    protected Optional<Uni<Object>> validateTransactionalType(InvocationContext context) {
        // Required is supported
        return Optional.empty();
    }
}
