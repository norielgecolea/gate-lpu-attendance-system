package org.nors.dev.codes.lpu.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class KioskPingScheduler {

    private final NotificationService notificationService;

    public KioskPingScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelay = 5000)
    public void pingKiosks() {
        notificationService.pingKiosks();
    }
}
