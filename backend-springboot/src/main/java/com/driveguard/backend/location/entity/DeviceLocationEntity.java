package com.driveguard.backend.location.entity;

import com.driveguard.backend.device.entity.DeviceEntity;
import com.driveguard.backend.trip.entity.TripEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "device_locations")
public class DeviceLocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private DeviceEntity device;

    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_m", precision = 8, scale = 2)
    private BigDecimal accuracyM;

    @Column(name = "speed_kmh", precision = 8, scale = 2)
    private BigDecimal speedKmh;

    @Column(name = "heading_deg", precision = 6, scale = 2)
    private BigDecimal headingDeg;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(
        name = "created_at",
        insertable = false,
        updatable = false
    )
    private LocalDateTime createdAt;

    protected DeviceLocationEntity() {
    }

    public DeviceLocationEntity(
            DeviceEntity device,
            String messageId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyM,
            BigDecimal speedKmh,
            BigDecimal headingDeg,
            LocalDateTime measuredAt,
            LocalDateTime receivedAt
    ) {
        this.device = device;
        this.messageId = messageId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyM = accuracyM;
        this.speedKmh = speedKmh;
        this.headingDeg = headingDeg;
        this.measuredAt = measuredAt;
        this.receivedAt = receivedAt;
    }

    // GPS có thể không thuộc chuyến đi nào.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "trip_id", nullable = true)
    private TripEntity trip;

    public Long getId() {
        return id;
    }

    public DeviceEntity getDevice() {
        return device;
    }

    public String getMessageId() {
        return messageId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public BigDecimal getAccuracyM() {
        return accuracyM;
    }

    public BigDecimal getSpeedKmh() {
        return speedKmh;
    }

    public BigDecimal getHeadingDeg() {
        return headingDeg;
    }

    public LocalDateTime getMeasuredAt() {
        return measuredAt;
    }

    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public TripEntity getTrip() {
        return trip;
    }

    public void setTrip(TripEntity trip) {
        this.trip = trip;
    }
}