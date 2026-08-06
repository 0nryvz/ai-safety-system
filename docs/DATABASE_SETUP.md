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

