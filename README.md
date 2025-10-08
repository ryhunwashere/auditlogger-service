# Minecraft Audit Logging Service
Standalone logging backend microservice to be paired with auditting server plugin.<br>
Utilizes Undertow HTTP using REST API architecture. <br>

This backend service requires a PostgreSQL database server instance to be already running before this JVM starts. <br> Otherwise, an error will be thrown.

All endpoints except `/token` requires a JWT bearer token to be accessed.
## Routes/Endpoints
| Method        | Route         | Description
| ------------- |:-------------:|:-------------:
| POST          | <p align="left">`/token`        | <p align="left">Public endpoint to acquire JWT token.
| POST          | <p align="left">`/logs`       | <p align="left">Post logs into a queue for batch insert into a PostgreSQL database.
| GET           | <p align="left">`/logs`     | <p align="left">Query to get logs of a specific player or logs of actions happened on given NxN area.
