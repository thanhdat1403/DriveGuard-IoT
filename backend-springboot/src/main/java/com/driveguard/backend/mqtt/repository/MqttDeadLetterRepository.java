package com.driveguard.backend.mqtt.repository;

import com.driveguard.backend.mqtt.entity.MqttDeadLetterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MqttDeadLetterRepository
        extends JpaRepository<MqttDeadLetterEntity, Long> {
}