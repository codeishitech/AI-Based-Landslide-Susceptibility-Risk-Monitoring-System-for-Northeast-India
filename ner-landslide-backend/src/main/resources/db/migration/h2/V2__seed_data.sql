-- V2__seed_data.sql (H2 version)
-- Sample risk zones across the North Eastern Region for local development

INSERT INTO risk_zones (code, name, state, district, centroid, current_risk_level,
                         population_exposure, infrastructure_criticality, nearby_villages, affected_roads)
VALUES
    ('NER-101', 'Shillong Peak Slope', 'Meghalaya', 'East Khasi Hills',
     ST_SetSRID(ST_MakePoint(91.8933, 25.5788), 4326), 'MEDIUM',
     3200, 4, 'Laitkor, Mawkasiang', 'Shillong-Cherrapunji Road'),

    ('NER-102', 'Mawsynram Escarpment', 'Meghalaya', 'East Khasi Hills',
     ST_SetSRID(ST_MakePoint(91.5822, 25.2977), 4326), 'LOW',
     1500, 2, 'Mawsynram village', 'NH206'),

    ('NER-103', 'Gangtok Ridge', 'Sikkim', 'East Sikkim',
     ST_SetSRID(ST_MakePoint(88.6138, 27.3389), 4326), 'HIGH',
     8000, 5, 'Tadong, Ranipool', 'NH10'),

    ('NER-104', 'Kohima Hillside', 'Nagaland', 'Kohima',
     ST_SetSRID(ST_MakePoint(94.1077, 25.6751), 4326), 'MEDIUM',
     5200, 3, 'Jotsoma, Kigwema', 'NH29'),

    ('NER-105', 'Aizawl Slope Zone', 'Mizoram', 'Aizawl',
     ST_SetSRID(ST_MakePoint(92.7173, 23.7271), 4326), 'LOW',
     6100, 4, 'Durtlang, Zemabawk', 'NH54'),

    ('NER-106', 'Itanagar Foothills', 'Arunachal Pradesh', 'Papum Pare',
     ST_SetSRID(ST_MakePoint(93.6053, 27.0844), 4326), 'MEDIUM',
     2800, 3, 'Naharlagun, Chimpu', 'NH415');
