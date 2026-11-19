
package com.driveguard.backend.device.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@Profile("e2e")
@EnableScheduling
public class E2eSchedulingConfiguration {
}
