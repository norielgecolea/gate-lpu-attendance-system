package org.nors.dev.codes.lpu.repository;

import java.util.Optional;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.nors.dev.codes.lpu.model.AppSetting;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AppSettingRepository {

    private final SessionFactory sessionFactory;

    public AppSettingRepository(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    private Session currentSession() {
        return sessionFactory.getCurrentSession();
    }

    @Transactional(readOnly = true)
    public Optional<AppSetting> findByKey(String key) {
        return Optional.ofNullable(currentSession().find(AppSetting.class, key));
    }

    @Transactional
    public AppSetting save(AppSetting setting) {
        return currentSession().merge(setting);
    }
}
