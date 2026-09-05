package com.ner.landslide.config;

import com.ner.landslide.entity.Alert;
import com.ner.landslide.entity.Profile;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.WeatherData;
import com.ner.landslide.entity.enums.AlertSeverity;
import com.ner.landslide.entity.enums.AlertStatus;
import com.ner.landslide.entity.enums.RiskLevel;
import com.ner.landslide.entity.enums.Role;
import com.ner.landslide.repository.AlertRepository;
import com.ner.landslide.repository.ProfileRepository;
import com.ner.landslide.repository.RiskZoneRepository;
import com.ner.landslide.repository.WeatherDataRepository;
import com.ner.landslide.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RiskZoneRepository riskZoneRepository;
    private final AlertRepository alertRepository;
    private final WeatherDataRepository weatherDataRepository;
    private final ProfileRepository profileRepository;

    @Override
    public void run(String... args) {
        if (riskZoneRepository.count() > 0) {
            log.info("Database already contains {} risk zones. Skipping seeding.", riskZoneRepository.count());
            return;
        }

        log.info("Seeding initial NER landslide risk zones and sample operational data...");

        RiskZone rz1 = RiskZone.builder()
                .code("NER-101")
                .name("Shillong Peak Slope")
                .state("Meghalaya")
                .district("East Khasi Hills")
                .centroid(GeoUtils.point(25.5788, 91.8933))
                .currentRiskLevel(RiskLevel.MEDIUM)
                .populationExposure(3200)
                .infrastructureCriticality(4)
                .nearbyVillages("Laitkor, Mawkasiang")
                .affectedRoads("Shillong-Cherrapunji Road")
                .lastUpdated(Instant.now())
                .build();

        RiskZone rz2 = RiskZone.builder()
                .code("NER-102")
                .name("Mawsynram Escarpment")
                .state("Meghalaya")
                .district("East Khasi Hills")
                .centroid(GeoUtils.point(25.2977, 91.5822))
                .currentRiskLevel(RiskLevel.LOW)
                .populationExposure(1500)
                .infrastructureCriticality(2)
                .nearbyVillages("Mawsynram Village")
                .affectedRoads("NH206")
                .lastUpdated(Instant.now())
                .build();

        RiskZone rz3 = RiskZone.builder()
                .code("NER-103")
                .name("Gangtok Ridge")
                .state("Sikkim")
                .district("East Sikkim")
                .centroid(GeoUtils.point(27.3389, 88.6138))
                .currentRiskLevel(RiskLevel.HIGH)
                .populationExposure(8000)
                .infrastructureCriticality(5)
                .nearbyVillages("Tadong, Ranipool")
                .affectedRoads("NH10")
                .lastUpdated(Instant.now())
                .build();

        RiskZone rz4 = RiskZone.builder()
                .code("NER-104")
                .name("Kohima Hillside")
                .state("Nagaland")
                .district("Kohima")
                .centroid(GeoUtils.point(25.6751, 94.1077))
                .currentRiskLevel(RiskLevel.MEDIUM)
                .populationExposure(5200)
                .infrastructureCriticality(3)
                .nearbyVillages("Jotsoma, Kigwema")
                .affectedRoads("NH29")
                .lastUpdated(Instant.now())
                .build();

        RiskZone rz5 = RiskZone.builder()
                .code("NER-105")
                .name("Aizawl Slope Zone")
                .state("Mizoram")
                .district("Aizawl")
                .centroid(GeoUtils.point(23.7271, 92.7173))
                .currentRiskLevel(RiskLevel.LOW)
                .populationExposure(6100)
                .infrastructureCriticality(4)
                .nearbyVillages("Durtlang, Zemabawk")
                .affectedRoads("NH54")
                .lastUpdated(Instant.now())
                .build();

        RiskZone rz6 = RiskZone.builder()
                .code("NER-106")
                .name("Itanagar Foothills")
                .state("Arunachal Pradesh")
                .district("Papum Pare")
                .centroid(GeoUtils.point(27.0844, 93.6053))
                .currentRiskLevel(RiskLevel.MEDIUM)
                .populationExposure(2800)
                .infrastructureCriticality(3)
                .nearbyVillages("Naharlagun, Chimpu")
                .affectedRoads("NH415")
                .lastUpdated(Instant.now())
                .build();

        List<RiskZone> savedZones = riskZoneRepository.saveAll(List.of(rz1, rz2, rz3, rz4, rz5, rz6));
        log.info("Successfully seeded {} risk zones.", savedZones.size());

        // Seed sample alert for high-risk Gangtok Ridge
        Alert alert1 = Alert.builder()
                .riskZone(rz3)
                .severity(AlertSeverity.HIGH)
                .status(AlertStatus.ACTIVE)
                .title("Elevated Landslide Hazard: NH10 Corridor")
                .message("High saturation and steep slope movements detected along NH10. Caution advised.")
                .affectedRadius(5.0)
                .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS))
                .build();

        Alert alert2 = Alert.builder()
                .riskZone(rz1)
                .severity(AlertSeverity.MEDIUM)
                .status(AlertStatus.ACTIVE)
                .title("Monsoon Soil Saturation Alert")
                .message("Shillong Peak slopes experiencing prolonged rain. Surface runoff monitoring active.")
                .affectedRadius(3.5)
                .expiresAt(Instant.now().plus(48, ChronoUnit.HOURS))
                .build();

        alertRepository.saveAll(List.of(alert1, alert2));
        log.info("Successfully seeded sample alerts.");

        // Seed initial weather readings
        WeatherData wd1 = WeatherData.builder()
                .riskZone(rz1)
                .rainfallMm(42.5)
                .forecastRainfallMm(65.0)
                .temperatureCelsius(19.5)
                .humidityPercent(88.0)
                .warningLevel("ADVISORY")
                .source("AUTOMATED_STATION")
                .observedAt(Instant.now())
                .build();

        WeatherData wd2 = WeatherData.builder()
                .riskZone(rz3)
                .rainfallMm(78.2)
                .forecastRainfallMm(120.0)
                .temperatureCelsius(16.0)
                .humidityPercent(94.0)
                .warningLevel("WARNING")
                .source("IMD_RADAR")
                .observedAt(Instant.now())
                .build();

        weatherDataRepository.saveAll(List.of(wd1, wd2));

        // Seed default system profile
        Profile adminProfile = Profile.builder()
                .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .fullName("NER Disaster Command Administrator")
                .phone("+91-9876543210")
                .role(Role.SUPER_ADMIN)
                .district("East Khasi Hills")
                .active(true)
                .createdAt(Instant.now())
                .build();

        profileRepository.save(adminProfile);
        log.info("Data seeding complete.");
    }
}
