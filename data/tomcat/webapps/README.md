# Deploy the backend WAR to Tomcat

1. Build:
      mvn -f lpu-attendance-system clean package
2. Copy the WAR into the deploy drop path (both replicas copy this file on start):
      cp lpu-attendance-system/target/attendance-system.war data/tomcat/webapps/
3. Ensure Postgres has been initialized (data/postgres/init/01_users.sql).
   If the DB volume already exists without the users table, run the SQL manually
   against the postgres container.
4. Start stack:
      docker compose up -d

nginx (:80) load-balances `/attendance-system/` across `tomcat-1` and `tomcat-2`.
Redis shares WebSocket broadcasts and kiosk presence between the two JVMs.

- Clustered app: http://localhost/ (nginx → Angular + both Tomcats)
- Direct replica: http://localhost:8080/attendance-system/ (`tomcat-1` only)
- Health: GET http://localhost/attendance-system/api/health

Login API: POST http://localhost/attendance-system/api/auth/login
WebSocket:  ws://localhost/attendance-system/ws/notifications?token=<JWT>

Context path: /attendance-system

Default SUPERADMIN (local only): superadmin / SuperAdmin@123
