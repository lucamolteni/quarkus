package io.quarkus.narayana.jta.runtime.interceptor;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.Transactional;

import io.quarkus.runtime.BlockingOperationControl;
import io.quarkus.runtime.BlockingOperationNotAllowedException;
import io.smallrye.mutiny.Uni;

/**
 * @author paul.robinson@redhat.com 25/05/2013
 */

@Interceptor
@Transactional(Transactional.TxType.REQUIRED)
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 200)
public class TransactionalInterceptorRequired extends TransactionalInterceptorBase {
    public TransactionalInterceptorRequired() {
        super(false);
    }

    @Override
    @AroundInvoke
    public Object intercept(InvocationContext ic) throws Exception {
        // Disable Interceptor on Reactive (uni) methods
        // in The Reactive transaction module only REQUIRED is supported so far
        if (ic.getMethod().getReturnType().equals(Uni.class)) {
            log.info("method is annoted @Transactional but returns a Uni<?>, JTA transactions will be disabled");
            return ic.proceed();
        }

        if (!BlockingOperationControl.isBlockingAllowed()) {
            throw new BlockingOperationNotAllowedException("Cannot start a JTA transaction from the IO thread.");
        }
        return super.intercept(ic);
    }

    @Override
    protected Object doIntercept(TransactionManager tm, Transaction tx, InvocationContext ic) throws Exception {
        if (tx == null) {
            return invokeInOurTx(ic, tm);
        } else {
            return invokeInCallerTx(ic, tx);
        }
    }
}
