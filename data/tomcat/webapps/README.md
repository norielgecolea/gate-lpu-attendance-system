# Deploy the backend WAR to Tomcat
#
# 1. Build:
#      mvn -f lpu-attendance-system clean package
# 2. Copy into the Tomcat webapps mount:
#      cp lpu-attendance-system/target/attendance-system.war data/tomcat/webapps/
# 3. Ensure Postgres has been initialized (data/postgres/init/01_users.sql).
#    If the DB volume already exists without the users table, run the SQL manually
#    against the postgres container.
# 4. Start stack:
#      docker compose up -d postgres tomcat
#
# Context path: /attendance-system
# Login API: POST http://localhost:8080/attendance-system/api/auth/login
# WebSocket:  ws://localhost:8080/attendance-system/ws/notifications?token=<JWT>
#
# Default SUPERADMIN (local only): superadmin / SuperAdmin@123
