package org.nors.dev.codes.lpu.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** An unrecognized RFID / ID tap at a gate (record not found). */
@Entity
@Table(name = "tap_error_logs")
public class TapErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String identifier;

    @Column(length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "kiosk_group", nullable = false, length = 20)
    private KioskGroup kioskGroup = KioskGroup.MAIN_GATES;

    @Column(name = "tapped_at", nullable = false)
    private Instant tappedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public KioskGroup getKioskGroup() {
        return kioskGroup;
    }

    public void setKioskGroup(KioskGroup kioskGroup) {
        this.kioskGroup = kioskGroup;
    }

    public Instant getTappedAt() {
        return tappedAt;
    }

    public void setTappedAt(Instant tappedAt) {
        this.tappedAt = tappedAt;
    }
}
