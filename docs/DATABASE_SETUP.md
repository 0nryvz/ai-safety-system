Database Setup

Overview

This project uses PostgreSQL as the primary database.

Database configuration is managed using environment variables.

Sensitive information such as passwords must not be committed to GitHub.

Local Development

For local development, PostgreSQL can be started using Docker Compose.

Required environment variables:

POSTGRES\_DB=isg\_db

POSTGRES\_USER=isg\_user

POSTGRES\_PASSWORD=<your\_password>



SPRING\_DATASOURCE\_URL=jdbc:postgresql://localhost:5432/isg\_db

SPRING\_DATASOURCE\_USERNAME=isg\_user

SPRING\_DATASOURCE\_PASSWORD=<your\_password>

Demo Seed Data

Demo data is stored separately from Flyway migrations at:

backend/src/main/resources/db/seed/demo-seed.sql

The seed is intended only for local/demo or test databases. It is not applied automatically by Flyway and must not be included in production migrations.

After Flyway has created the database schema, copy the seed file into the PostgreSQL container:

docker cp backend/src/main/resources/db/seed/demo-seed.sql isg-postgres:/tmp/demo-seed.sql

Apply the seed to the target local/demo database:

docker exec isg-postgres psql -v ON_ERROR_STOP=1 -U isg_user -d isg_db -f /tmp/demo-seed.sql

ON_ERROR_STOP=1 stops the command immediately if any seed statement fails.

The demo dataset includes users, departments, cameras, camera sessions, violations and recordings.

Demo user password: 123456

Do not run the demo seed against a production database.

