# Zahra v0.17.0 status

Realistic Life World is now the active visual target. The world remains offline-first and procedural so the Android package does not depend on remote asset downloads. v0.16 simulation systems remain intact.

## Visual scope
- Human-like procedural NPC bodies with simple facial features and walk/idle animation.
- Detailed streets, lane markings, sidewalks, cars, trees, building glazing, rooftop equipment and district signage.
- Mobile renderer, ACES tonemapping and dynamic sun shadows.

## Limitation
This is a procedural realistic foundation, not a photorealistic AAA asset pack. For the final photorealistic tier, production humanoid/architecture assets and animation clips can be imported later without changing the simulation architecture.

# Zahra v0.16.0 status

Zahra v0.16.0 mengimplementasikan perluasan penuh dari fondasi v0.15.0: dunia 3D kota hidup, simulasi ekonomi-politik-agama yang terhubung, NPC berlapis, dan Android World Command Center.

## Pilar simulasi
- **Agama:** salat, Ramadan, kajian, masjid, komunitas, zakat, sedekah, wakaf, dan kalender.
- **Bisnis:** usaha, karyawan, pemasok, produksi, stok, strategi, etika, investasi, pajak, dan market share.
- **Politik:** faksi, forum, proposal, kebijakan, anggaran, koalisi, pemilu fiktif, dan jenjang karier pelayanan.
- **Kota:** layanan publik, pendidikan, kesehatan, mobilitas, keselamatan, dan aktivitas ekonomi.

## Optimasi
- Geometri kota memakai primitive low-poly dan material cache.
- Visibility range diterapkan pada objek utama.
- NPC tetap di-update dengan budget waktu dan jadwal.
- Save tetap compressed + integrity checked + atomic.
- Bridge tetap authenticated/durable/offline-first.
