# Minecraft Audit Logging Service
Logging backend service to be paired with auditting server plugin.<br>
Utilizes Undertow HTTP using REST API architecture. <br>

This service requires a PostgreSQL database server instance to be already running before this JVM starts. <br>
Otherwise, an error will be thrown.

`/logs` endpoints requires a JWT bearer token to be accessed. The token can be retrieved from the public `/token` route.
## Routes/Endpoints
| Method        | Route         | Description
| ------------- |:-------------:|:-------------:
| POST          | <p align="left">`/token`        | <p align="left">Public endpoint to acquire JWT token.
| POST          | <p align="left">`/logs`       | <p align="left">Post logs into a queue for batch insert into a PostgreSQL database.
| GET           | <p align="left">`/logs`     | <p align="left">Query to get logs of a specific player or logs of actions happened on given NxN area.

## API Contract
All POST endpoints must be provided with accurate format provided below. Otherwise, a status code of 400 (Bad request) will be returned.
### 1. POST `/token` <br>
To acquire a JWT token, client must send a JSON with provided format like below:
   ```
   {
     "issuer": "mc-server",
     "secret": "SuperSecretPassword42069"
   }
   ```
   * If the issuer and secret values are valid, it will return a status code of 200 and JSON with `"token"` key.
   * Invalid credentials will return status code of 400 (Unauthorized).
