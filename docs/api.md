# API Documentation

## Authentication

### POST /api/auth/signup
- Content-Type: application/x-www-form-urlencoded
- body: username, email, password, password_confirm
- success: 201 Created, body `signup successful`
- errors: 400, 409

### POST /api/auth/login
- Content-Type: application/x-www-form-urlencoded
- body: username, password
- success: 200 OK, JSON:
  - `{"message":"login successful","username":"...","email":"..."}`
- errors: 400, 401

### GET /api/auth/me
- requires cookie `AUTH_TOKEN`
- success: 200 OK, JSON `{"username":"...","email":"..."}`
- errors: 401, 404

## Games

### GET /api/games
- success: 200 OK, JSON array `[{"name":"Chess","maxPlayers":2},{...}]`

### GET /api/games/{name}
- success: 200 OK, JSON `{"name":"Chess","maxPlayers":2}`
- errors: 400, 404

### POST /api/games/{name}/start
- requires valid game name
- success: 200 OK, text `game started: ...`
- errors: 400, 404

## Web (UI) endpoints

- GET / -> login page
- GET /signup -> signup page
- POST /login -> form login (redirect to /games)
- POST /signup -> form signup (redirect to /games)
- GET /games -> games dashboard (requires login)
- GET /games/{name} -> game details

## DB settings

The app uses H2 by default: `jdbc:h2:./build/db/games-night;MODE=PostgreSQL;DB_CLOSE_DELAY=-1`.

For production, set environment variables:
- `DB_URL` - JDBC URL
- `DB_USER` - username
- `DB_PASSWORD` - password
