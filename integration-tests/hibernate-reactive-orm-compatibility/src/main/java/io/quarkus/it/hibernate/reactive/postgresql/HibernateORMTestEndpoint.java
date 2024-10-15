package io.quarkus.it.hibernate.reactive.postgresql;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

import org.hibernate.Session;

import io.quarkus.security.Authenticated;

@Path("/testsORM")
@Authenticated
public class HibernateORMTestEndpoint {

    @Inject
    Session session;

    @GET
    @Path("/blockingCowPersist")
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public FriesianCow reactiveCowPersist() {
        final FriesianCow cow = new FriesianCow();
        cow.name = "Carolina";

        session.persist(cow);
        return session.createQuery("from FriesianCow f where f.name = :name", FriesianCow.class)
                .setParameter("name", cow.name).getSingleResult();
    }
}
