# Demo Plan: Keycloak Authentication Flow

## 1. Local Runtime

The system runs locally with Angular, Spring Boot, and PostgreSQL. Keycloak is the remote identity provider.

- Frontend: `http://localhost:4200`
- Backend: `http://localhost:8080`
- PostgreSQL: `localhost:5432`
- Database: `app_db`
- Keycloak: `https://id.smartsolutionvn.com.vn`
- Realm: `ssvn`
- Client ID: `ssvn-platform-client-id`
- Backend callback URI: `http://localhost:8080/api/auth/callback`

Run backend:

```powershell
cd C:\project\task1\backend
mvn spring-boot:run
```

Run frontend:

```powershell
cd C:\project\task1\frontend
npm start
```

PostgreSQL setup in DBeaver:

```sql
CREATE DATABASE app_db;
```

Spring Boot creates/updates table `app_users` and loads seed data from `backend/src/main/resources/data.sql`.

## 2. Component Responsibilities

### Frontend Angular

- Shows the login/profile UI.
- Redirects the browser directly to the Keycloak authorization endpoint.
- Reads tokens from the URL fragment after backend callback redirects back to Angular.
- Keeps tokens in memory only, not in `localStorage`.
- Calls backend API `GET /api/users/me`.
- Uses `authInterceptor` to attach `Authorization: Bearer <access_token>` to protected backend API calls.

Main functions:

- `KeycloakService.login()`: clears runtime token, builds the Keycloak authorize URL, and redirects to Keycloak.
- `KeycloakService.handleAuthRedirect()`: reads `access_token` and `refresh_token` from the URL fragment, stores them in memory, then removes the fragment from the address bar.
- `KeycloakService.getValidToken()`: returns the current access token or refreshes it through `/api/auth/refresh`.
- `authInterceptor`: skips `/api/auth/**`, attaches Bearer token to other backend requests.
- `App.fetchUserProfile()`: calls `/api/users/me` and binds the returned profile to the UI.

### Backend Spring Boot

- Keeps `client_secret`; the frontend never receives or sends it.
- Receives Keycloak callback at `/api/auth/callback`.
- Exchanges authorization code for tokens at the Keycloak token endpoint.
- Refreshes tokens through `/api/auth/refresh`.
- Logs out through `/api/auth/logout`.
- Validates JWTs for protected APIs as an OAuth2 Resource Server.
- Loads application-specific user data from PostgreSQL.

Main functions:

- `AuthController.callback()`: receives `code`, exchanges it for tokens using `client_secret`, and redirects back to Angular with token values in the URL fragment.
- `AuthController.refresh()`: receives refresh token, calls Keycloak with `grant_type=refresh_token`, and returns new tokens.
- `AuthController.logout()`: sends refresh token to Keycloak logout endpoint.
- `SecurityConfig.securityFilterChain()`: permits `/api/auth/**`, protects all other APIs, and configures JWT validation.
- `UserController.getCurrentUser()`: reads the validated JWT, extracts username/roles, queries PostgreSQL by username, and returns profile data.

### Keycloak

- Authenticates the user on the Keycloak login page.
- Issues authorization code, access token, and refresh token.
- Signs JWTs and exposes realm metadata for backend validation.
- Stores identity claims and roles.

### PostgreSQL

- Stores app-specific user profile data.
- The demo uses `app_users.position` to prove that business data is loaded from the local database, not from Keycloak.

## 3. End-To-End Login Flow

1. User opens `http://localhost:4200`.
2. Angular shows the login screen.
3. User clicks **Login with Keycloak**.
4. `KeycloakService.login()` redirects the browser to:

   ```text
   https://id.smartsolutionvn.com.vn/realms/ssvn/protocol/openid-connect/auth
   ```

5. User enters username/password on the Keycloak page.
6. Keycloak redirects to:

   ```text
   http://localhost:8080/api/auth/callback?code=...
   ```

7. `AuthController.callback()` exchanges the code for tokens using:

   ```text
   client_id
   client_secret
   grant_type=authorization_code
   code
   redirect_uri
   ```

8. Backend redirects back to Angular with tokens in the URL fragment.
9. Angular reads the tokens into memory and removes the fragment from the URL.
10. Angular calls:

   ```text
   GET http://localhost:8080/api/users/me
   ```

11. `authInterceptor` attaches:

   ```text
   Authorization: Bearer <access_token>
   ```

12. Spring Security validates the JWT signature and issuer.
13. Backend extracts username, email, name, and roles from JWT claims.
14. Backend queries PostgreSQL table `app_users` by username.
15. Backend returns username, email, roles, and position.
16. Angular displays the profile.

Short explanation:

> Authentication is handled by Keycloak. Authorization and API protection are handled by Spring Security JWT validation. Application data such as position is loaded from PostgreSQL.

## 4. Demo Script

Use this order for a smooth 5-7 minute presentation.

1. Show architecture.
   - Angular is the client.
   - Spring Boot is the backend/resource server.
   - Keycloak is the identity provider.
   - PostgreSQL stores app-specific profile data.

2. Show database in DBeaver.
   - Open `app_db`.
   - Show table `app_users`.
   - Point to user `ssvn` and the `position` value.

3. Show backend configuration.
   - Datasource points to PostgreSQL local.
   - JWT issuer points to Keycloak realm `ssvn`.
   - `client_secret` is in backend config, not in Angular.

4. Run the app.
   - Start backend with `mvn spring-boot:run`.
   - Start frontend with `npm start`.
   - Open `http://localhost:4200`.

5. Demo login.
   - Click **Login with Keycloak**.
   - Browser moves to Keycloak.
   - Login with the demo account.
   - Browser returns to Angular.

6. Demo protected API.
   - Open browser Network tab.
   - Show request `/api/users/me`.
   - Show Bearer token header.
   - Explain that backend validates this token before returning data.

7. Demo database-backed position.
   - Show `position` in DBeaver.
   - Show the same position on the Angular UI.

8. Explain authentication vs authorization.
   - Authentication: Keycloak verifies who the user is.
   - Authorization: backend checks the validated token/roles before allowing API access.

9. Finish with the core result.
   - Login flow works end to end.
   - Secret is not exposed to frontend.
   - Backend protects APIs with JWT.
   - Position comes from local PostgreSQL.

## 5. Demo Checklist

- PostgreSQL service is running.
- Database `app_db` exists.
- Table `app_users` has user `ssvn`.
- Backend runs on `localhost:8080`.
- Frontend runs on `localhost:4200`.
- Keycloak client allows redirect URI:

  ```text
  http://localhost:8080/api/auth/callback
  ```

- For a clean login screen, use an incognito browser window or logout from Keycloak first.
- Token is memory-only. Reloading Angular clears the token and requires login again.

Pre-demo checks:

```powershell
cd C:\project\task1\backend
mvn test
```

```powershell
cd C:\project\task1\frontend
npm run build
```
