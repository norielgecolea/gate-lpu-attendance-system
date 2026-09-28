package org.nors.dev.codes.lpu.repository;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.ApiRequestAuditEvent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ApiRequestAuditEventRepository {

    private final SessionFactory sessionFactory;

    public ApiRequestAuditEventRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Transactional
    public void persist(ApiRequestAuditEvent event) {
        Session session = sessionFactory.getCurrentSession();
        session.persist(event);
        session.flush();
    }
}
