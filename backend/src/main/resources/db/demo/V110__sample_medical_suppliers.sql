-- Sample veterinary shops for the Medical Suppliers page. Not loaded by the prod profile.
-- These accounts have no farm role and cannot sign in.

INSERT INTO users (id, tenant_id, email, password_hash, full_name, job_title, status, access_scope, created_at) VALUES
('d2000001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'agrovet@shops.shambanexa.local', 'sample-shop-cannot-sign-in', 'AgroVet Supplies', 'Supplier', 'SAMPLE', 'OWN_FARM', TIMESTAMP WITH TIME ZONE '2026-01-15 08:00:00+00'),
('d2000002-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'kilimanjaro@shops.shambanexa.local', 'sample-shop-cannot-sign-in', 'Kilimanjaro Vet Care', 'Supplier', 'SAMPLE', 'OWN_FARM', TIMESTAMP WITH TIME ZONE '2026-01-15 08:00:00+00'),
('d2000003-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'farmcare@shops.shambanexa.local', 'sample-shop-cannot-sign-in', 'FarmCare Vet Shop', 'Supplier', 'SAMPLE', 'OWN_FARM', TIMESTAMP WITH TIME ZONE '2026-01-15 08:00:00+00'),
('d2000004-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'livestockmedics@shops.shambanexa.local', 'sample-shop-cannot-sign-in', 'Livestock Medics', 'Supplier', 'SAMPLE', 'OWN_FARM', TIMESTAMP WITH TIME ZONE '2026-01-15 08:00:00+00'),
('d2000005-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'nexavet@shops.shambanexa.local', 'sample-shop-cannot-sign-in', 'Nexa Vet Center', 'Supplier', 'SAMPLE', 'OWN_FARM', TIMESTAMP WITH TIME ZONE '2026-01-15 08:00:00+00');

INSERT INTO medical_supplier (
    id, tenant_id, owner_id, name, mark, region, district, address, phone, open_hours,
    latitude, longitude, license_number, summary, approval, shop_state, created_at
) VALUES
('e2000001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'd2000001-0000-4000-8000-000000000001',
 'AgroVet Supplies', 'LEAVES', 'Arusha', 'Arusha', 'Sokoke Road, Njiro, Arusha', '+255 754 123 456', 'Mon - Sat, 8:00 AM - 6:00 PM',
 -3.488310, 36.719979, 'TZ-VET-ARU-1042', 'Veterinary medicines, vaccines, supplements', 'APPROVED', 'OPEN', TIMESTAMP WITH TIME ZONE '2026-02-01 08:00:00+00'),
('e2000002-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'd2000002-0000-4000-8000-000000000002',
 'Kilimanjaro Vet Care', 'PEAK', 'Kilimanjaro', 'Moshi', 'Market Street, Moshi', '+255 754 123 457', 'Mon - Sat, 8:00 AM - 6:00 PM',
 -3.408814, 36.934296, 'TZ-VET-MOS-1188', 'Veterinary medicines, antibiotics, vaccines', 'APPROVED', 'OPEN', TIMESTAMP WITH TIME ZONE '2026-02-02 08:00:00+00'),
('e2000003-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'd2000003-0000-4000-8000-000000000003',
 'FarmCare Vet Shop', 'CATTLE', 'Tanga', 'Tanga', 'Independence Road, Tanga', '+255 754 123 458', 'Mon - Fri, 8:00 AM - 5:00 PM',
 -4.827747, 39.301945, 'TZ-VET-TAN-0904', 'Wide range of animal health products', 'APPROVED', 'OPEN', TIMESTAMP WITH TIME ZONE '2026-02-03 08:00:00+00'),
('e2000004-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'd2000004-0000-4000-8000-000000000004',
 'Livestock Medics', 'CROSS', 'Dodoma', 'Dodoma', 'Boma Road, Dodoma', '+255 754 123 459', 'Mon - Sat, 8:30 AM - 5:30 PM',
 -5.791019, 35.685674, 'TZ-VET-DOD-2210', 'Veterinary drugs and farm supplies', 'APPROVED', 'LIMITED', TIMESTAMP WITH TIME ZONE '2026-02-04 08:00:00+00'),
('e2000005-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'd2000005-0000-4000-8000-000000000005',
 'Nexa Vet Center', 'PAW', 'Morogoro', 'Morogoro', 'Station Road, Morogoro', '+255 754 123 460', 'Mon - Sat, 8:00 AM - 6:00 PM',
 -6.885804, 38.673044, 'TZ-VET-MOR-1765', 'Quality animal health solutions', 'APPROVED', 'OPEN', TIMESTAMP WITH TIME ZONE '2026-02-05 08:00:00+00');

INSERT INTO supplier_medicine (
    id, tenant_id, supplier_id, name, animal_types, category, price_amount, currency_code,
    quantity, unit_label, stock_state, prescription_required, created_at, summary
) VALUES
('f2000001-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'e2000001-0000-4000-8000-000000000001',
 'Ivermectin 1% (100ml)', 'Cattle, Goats, Sheep', 'Dewormers', 12000, 'TZS', 40, 'bottle', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-01 09:00:00+00', 'Dewormer for cattle, goats, sheep'),
('f2000001-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'e2000001-0000-4000-8000-000000000001',
 'Albendazole (500mg)', 'Cattle, Goats, Sheep', 'Dewormers', 8000, 'TZS', 80, 'pack', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-01 09:01:00+00', 'Broad spectrum dewormer'),
('f2000001-0000-4000-8000-000000000003', '11111111-1111-4111-8111-111111111111', 'e2000001-0000-4000-8000-000000000001',
 'Oxytetracycline (100ml)', 'Cattle', 'Antibiotics', 15000, 'TZS', 24, 'bottle', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-01 09:02:00+00', 'Antibiotic'),
('f2000001-0000-4000-8000-000000000004', '11111111-1111-4111-8111-111111111111', 'e2000001-0000-4000-8000-000000000001',
 'Multivitamin injection', 'Cattle, Goats, Sheep', 'Vitamins and minerals', 10000, 'TZS', 6, 'vial', 'LIMITED', false, TIMESTAMP WITH TIME ZONE '2026-02-01 09:03:00+00', 'Vitamins and minerals'),
('f2000001-0000-4000-8000-000000000005', '11111111-1111-4111-8111-111111111111', 'e2000001-0000-4000-8000-000000000001',
 'Foot Rot Spray (250ml)', 'Cattle, Goats, Sheep', 'Other', 18000, 'TZS', 18, 'bottle', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-01 09:04:00+00', 'Hoof care'),
('f2000002-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'e2000002-0000-4000-8000-000000000002',
 'Penicillin injection (100ml)', 'Cattle', 'Antibiotics', 14000, 'TZS', 20, 'bottle', 'IN_STOCK', true, TIMESTAMP WITH TIME ZONE '2026-02-02 09:00:00+00', 'Antibiotic for cattle'),
('f2000002-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'e2000002-0000-4000-8000-000000000002',
 'Newcastle vaccine (1000 doses)', 'Poultry', 'Vaccines', 22000, 'TZS', 12, 'vial', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-02 09:01:00+00', 'Poultry vaccine'),
('f2000003-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'e2000003-0000-4000-8000-000000000003',
 'Acaricide dip (1 litre)', 'Cattle, Goats, Sheep', 'Other', 16000, 'TZS', 15, 'bottle', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-03 09:00:00+00', 'Tick control'),
('f2000003-0000-4000-8000-000000000002', '11111111-1111-4111-8111-111111111111', 'e2000003-0000-4000-8000-000000000003',
 'Mineral block', 'Cattle, Goats, Sheep', 'Supplements', 6000, 'TZS', 30, 'piece', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-03 09:01:00+00', 'Mineral supplement'),
('f2000004-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'e2000004-0000-4000-8000-000000000004',
 'Wound spray (100ml)', 'Cattle, Goats, Sheep', 'Other', 9000, 'TZS', 4, 'bottle', 'LIMITED', false, TIMESTAMP WITH TIME ZONE '2026-02-04 09:00:00+00', 'Wound care'),
('f2000005-0000-4000-8000-000000000001', '11111111-1111-4111-8111-111111111111', 'e2000005-0000-4000-8000-000000000005',
 'Dewormer bolus', 'Cattle, Goats', 'Dewormers', 7500, 'TZS', 50, 'pack', 'IN_STOCK', false, TIMESTAMP WITH TIME ZONE '2026-02-05 09:00:00+00', 'Dewormer for cattle and goats');
