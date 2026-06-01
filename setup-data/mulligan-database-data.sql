-- Mulligan setup data for Assignment 1.
-- This file loads the database setup data required by the assignment:
-- the real customer vehicles, 10 parking zones with different costs, and 100 parking spaces.

INSERT INTO vehicles (plate_number, owner_name)
VALUES
    ('604-95-839', 'CUST-1001 Vehicle'),
    ('089-64-318', 'CUST-1002 Vehicle'),
    ('058-28-878', 'CUST-1003 Vehicle'),
    ('394-23-797', 'CUST-1004 Vehicle')
ON CONFLICT (plate_number) DO UPDATE SET
    owner_name = EXCLUDED.owner_name;

-- Connect each customer account to exactly one vehicle.
-- This does not update customer usernames, passwords, names, or active status.
UPDATE customers
SET vehicle_id = assigned.vehicle_id
FROM (
    VALUES
        ('CUST-1001', '604-95-839'),
        ('CUST-1002', '089-64-318'),
        ('CUST-1003', '058-28-878'),
        ('CUST-1004', '394-23-797')
) AS mapping(customer_id, plate_number)
JOIN vehicles assigned ON assigned.plate_number = mapping.plate_number
WHERE customers.customer_id = mapping.customer_id;

INSERT INTO parking_zones (zone_code, zone_name, hourly_rate, max_parking_minutes)
VALUES
    ('A1', 'Central Zone', 8.00, 120),
    ('A2', 'Mall Zone', 10.00, 180),
    ('B1', 'Residential North', 5.50, 240),
    ('B2', 'Residential South', 6.00, 240),
    ('C1', 'Hospital Zone', 7.50, 90),
    ('C2', 'University Zone', 4.50, 300),
    ('D1', 'Beach Zone', 12.00, 180),
    ('D2', 'Business District', 11.00, 120),
    ('E1', 'Old City', 9.00, 150),
    ('E2', 'Train Station', 6.50, 60)
ON CONFLICT (zone_code) DO UPDATE SET
    zone_name = EXCLUDED.zone_name,
    hourly_rate = EXCLUDED.hourly_rate,
    max_parking_minutes = EXCLUDED.max_parking_minutes;

INSERT INTO parking_spaces (space_number, zone_id, street_name, is_active)
SELECT
    data.space_number,
    parking_zones.zone_id,
    data.street_name,
    data.is_active
FROM (
    VALUES
        ('S001', 'A1', 'Central Zone Street', TRUE),
        ('S002', 'A1', 'Central Zone Street', TRUE),
        ('S003', 'A1', 'Central Zone Street', TRUE),
        ('S004', 'A1', 'Central Zone Street', TRUE),
        ('S005', 'A1', 'Central Zone Street', TRUE),
        ('S006', 'A1', 'Central Zone Street', TRUE),
        ('S007', 'A1', 'Central Zone Street', TRUE),
        ('S008', 'A1', 'Central Zone Street', TRUE),
        ('S009', 'A1', 'Central Zone Street', TRUE),
        ('S010', 'A1', 'Central Zone Street', TRUE),
        ('S011', 'A2', 'Mall Zone Street', TRUE),
        ('S012', 'A2', 'Mall Zone Street', TRUE),
        ('S013', 'A2', 'Mall Zone Street', TRUE),
        ('S014', 'A2', 'Mall Zone Street', TRUE),
        ('S015', 'A2', 'Mall Zone Street', TRUE),
        ('S016', 'A2', 'Mall Zone Street', TRUE),
        ('S017', 'A2', 'Mall Zone Street', TRUE),
        ('S018', 'A2', 'Mall Zone Street', TRUE),
        ('S019', 'A2', 'Mall Zone Street', TRUE),
        ('S020', 'A2', 'Mall Zone Street', TRUE),
        ('S021', 'B1', 'Residential North Street', TRUE),
        ('S022', 'B1', 'Residential North Street', TRUE),
        ('S023', 'B1', 'Residential North Street', TRUE),
        ('S024', 'B1', 'Residential North Street', TRUE),
        ('S025', 'B1', 'Residential North Street', TRUE),
        ('S026', 'B1', 'Residential North Street', TRUE),
        ('S027', 'B1', 'Residential North Street', TRUE),
        ('S028', 'B1', 'Residential North Street', TRUE),
        ('S029', 'B1', 'Residential North Street', TRUE),
        ('S030', 'B1', 'Residential North Street', TRUE),
        ('S031', 'B2', 'Residential South Street', TRUE),
        ('S032', 'B2', 'Residential South Street', TRUE),
        ('S033', 'B2', 'Residential South Street', TRUE),
        ('S034', 'B2', 'Residential South Street', TRUE),
        ('S035', 'B2', 'Residential South Street', TRUE),
        ('S036', 'B2', 'Residential South Street', TRUE),
        ('S037', 'B2', 'Residential South Street', TRUE),
        ('S038', 'B2', 'Residential South Street', TRUE),
        ('S039', 'B2', 'Residential South Street', TRUE),
        ('S040', 'B2', 'Residential South Street', TRUE),
        ('S041', 'C1', 'Hospital Zone Street', TRUE),
        ('S042', 'C1', 'Hospital Zone Street', TRUE),
        ('S043', 'C1', 'Hospital Zone Street', TRUE),
        ('S044', 'C1', 'Hospital Zone Street', TRUE),
        ('S045', 'C1', 'Hospital Zone Street', TRUE),
        ('S046', 'C1', 'Hospital Zone Street', TRUE),
        ('S047', 'C1', 'Hospital Zone Street', TRUE),
        ('S048', 'C1', 'Hospital Zone Street', TRUE),
        ('S049', 'C1', 'Hospital Zone Street', TRUE),
        ('S050', 'C1', 'Hospital Zone Street', TRUE),
        ('S051', 'C2', 'University Zone Street', TRUE),
        ('S052', 'C2', 'University Zone Street', TRUE),
        ('S053', 'C2', 'University Zone Street', TRUE),
        ('S054', 'C2', 'University Zone Street', TRUE),
        ('S055', 'C2', 'University Zone Street', TRUE),
        ('S056', 'C2', 'University Zone Street', TRUE),
        ('S057', 'C2', 'University Zone Street', TRUE),
        ('S058', 'C2', 'University Zone Street', TRUE),
        ('S059', 'C2', 'University Zone Street', TRUE),
        ('S060', 'C2', 'University Zone Street', TRUE),
        ('S061', 'D1', 'Beach Zone Street', TRUE),
        ('S062', 'D1', 'Beach Zone Street', TRUE),
        ('S063', 'D1', 'Beach Zone Street', TRUE),
        ('S064', 'D1', 'Beach Zone Street', TRUE),
        ('S065', 'D1', 'Beach Zone Street', TRUE),
        ('S066', 'D1', 'Beach Zone Street', TRUE),
        ('S067', 'D1', 'Beach Zone Street', TRUE),
        ('S068', 'D1', 'Beach Zone Street', TRUE),
        ('S069', 'D1', 'Beach Zone Street', TRUE),
        ('S070', 'D1', 'Beach Zone Street', TRUE),
        ('S071', 'D2', 'Business District Street', TRUE),
        ('S072', 'D2', 'Business District Street', TRUE),
        ('S073', 'D2', 'Business District Street', TRUE),
        ('S074', 'D2', 'Business District Street', TRUE),
        ('S075', 'D2', 'Business District Street', TRUE),
        ('S076', 'D2', 'Business District Street', TRUE),
        ('S077', 'D2', 'Business District Street', TRUE),
        ('S078', 'D2', 'Business District Street', TRUE),
        ('S079', 'D2', 'Business District Street', TRUE),
        ('S080', 'D2', 'Business District Street', TRUE),
        ('S081', 'E1', 'Old City Street', TRUE),
        ('S082', 'E1', 'Old City Street', TRUE),
        ('S083', 'E1', 'Old City Street', TRUE),
        ('S084', 'E1', 'Old City Street', TRUE),
        ('S085', 'E1', 'Old City Street', TRUE),
        ('S086', 'E1', 'Old City Street', TRUE),
        ('S087', 'E1', 'Old City Street', TRUE),
        ('S088', 'E1', 'Old City Street', TRUE),
        ('S089', 'E1', 'Old City Street', TRUE),
        ('S090', 'E1', 'Old City Street', TRUE),
        ('S091', 'E2', 'Train Station Street', TRUE),
        ('S092', 'E2', 'Train Station Street', TRUE),
        ('S093', 'E2', 'Train Station Street', TRUE),
        ('S094', 'E2', 'Train Station Street', TRUE),
        ('S095', 'E2', 'Train Station Street', TRUE),
        ('S096', 'E2', 'Train Station Street', TRUE),
        ('S097', 'E2', 'Train Station Street', TRUE),
        ('S098', 'E2', 'Train Station Street', TRUE),
        ('S099', 'E2', 'Train Station Street', TRUE),
        ('S100', 'E2', 'Train Station Street', TRUE)
) AS data(space_number, zone_code, street_name, is_active)
JOIN parking_zones ON parking_zones.zone_code = data.zone_code
ON CONFLICT (space_number) DO UPDATE SET
    zone_id = EXCLUDED.zone_id,
    street_name = EXCLUDED.street_name,
    is_active = EXCLUDED.is_active;
