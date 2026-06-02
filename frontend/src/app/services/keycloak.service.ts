import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

const API_BASE_URL = 'http://localhost:8080/api';
const KEYCLOAK_AUTH_URL = 'https://id.smartsolutionvn.com.vn/realms/ssvn/protocol/openid-connect/auth';
const KEYCLOAK_CLIENT_ID = 'ssvn-platform-client-id';
const BACKEND_CALLBACK_URL = 'http://localhost:8080/api/auth/callback';

interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
  tokenType: string;
}

export interface AuthenticatedUser {
  username: string;
  email: string;
  fullName: string;
}

@Injectable({
  providedIn: 'root'
})
export class KeycloakService {
  private readonly http = inject(HttpClient);
  private accessToken = '';
  private refreshTokenValue = '';

  init(): boolean {
    this.handleAuthRedirect();
    return this.isLoggedIn();
  }

  login(): void {
    this.clearToken();
    const params = new URLSearchParams({
      client_id: KEYCLOAK_CLIENT_ID,
      redirect_uri: BACKEND_CALLBACK_URL,
      response_type: 'code',
      scope: 'openid profile email',
      prompt: 'login',
      state: crypto.randomUUID()
    });

    window.location.href = `${KEYCLOAK_AUTH_URL}?${params.toString()}`;
  }

  async logout(): Promise<void> {
    try {
      if (this.refreshTokenValue) {
        await firstValueFrom(this.http.post<void>(`${API_BASE_URL}/auth/logout`, {
          refreshToken: this.refreshTokenValue
        }));
      }
    } catch (error) {
      console.error('Keycloak logout error:', error);
    } finally {
      this.clearToken();
    }
  }

  async getValidToken(): Promise<string> {
    const accessToken = this.getToken();
    if (!accessToken) {
      return '';
    }

    if (!this.isTokenExpiring(accessToken, 30)) {
      return accessToken;
    }

    return this.refreshToken();
  }

  getToken(): string {
    return this.accessToken;
  }

  isLoggedIn(): boolean {
    const token = this.getToken();
    return !!token && !this.isTokenExpiring(token, 0);
  }

  getCurrentUser(): AuthenticatedUser {
    const token = this.decodeToken(this.getToken());
    const username = token?.['preferred_username'] as string | undefined;
    const email = token?.['email'] as string | undefined;
    const fullName = token?.['name'] as string | undefined;

    return {
      username: username ?? '',
      email: email ?? '',
      fullName: fullName ?? username ?? ''
    };
  }

  getUsername(): string {
    return this.getCurrentUser().username;
  }

  getEmail(): string {
    return this.getCurrentUser().email;
  }

  getFullName(): string {
    return this.getCurrentUser().fullName;
  }

  private async refreshToken(): Promise<string> {
    if (!this.refreshTokenValue) {
      this.clearToken();
      return '';
    }

    try {
      const token = await firstValueFrom(this.http.post<AuthTokenResponse>(`${API_BASE_URL}/auth/refresh`, {
        refreshToken: this.refreshTokenValue
      }));
      this.storeToken(token);
      return token.accessToken;
    } catch (error) {
      console.error('Keycloak refresh token error:', error);
      this.clearToken();
      return '';
    }
  }

  private storeToken(token: AuthTokenResponse): void {
    this.accessToken = token.accessToken;
    this.refreshTokenValue = token.refreshToken;
  }

  private handleAuthRedirect(): void {
    if (!window.location.hash) {
      return;
    }

    const params = new URLSearchParams(window.location.hash.substring(1));
    const error = params.get('error');
    if (error) {
      console.error('Keycloak redirect error:', error, params.get('error_description'));
      this.clearUrlFragment();
      return;
    }

    const accessToken = params.get('access_token');
    const refreshToken = params.get('refresh_token');
    if (!accessToken || !refreshToken) {
      return;
    }

    this.storeToken({
      accessToken,
      refreshToken,
      expiresIn: Number(params.get('expires_in') ?? 0),
      refreshExpiresIn: Number(params.get('refresh_expires_in') ?? 0),
      tokenType: params.get('token_type') ?? 'Bearer'
    });
    this.clearUrlFragment();
  }

  private clearUrlFragment(): void {
    history.replaceState(null, document.title, `${window.location.pathname}${window.location.search}`);
  }

  private clearToken(): void {
    this.accessToken = '';
    this.refreshTokenValue = '';
  }

  private isTokenExpiring(token: string, skewSeconds: number): boolean {
    const decoded = this.decodeToken(token);
    const expiresAt = decoded?.['exp'] as number | undefined;

    if (!expiresAt) {
      return true;
    }

    return Date.now() >= (expiresAt - skewSeconds) * 1000;
  }

  private decodeToken(token: string): Record<string, unknown> | null {
    if (!token) {
      return null;
    }

    try {
      const payload = token.split('.')[1];
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const json = decodeURIComponent(
        atob(base64)
          .split('')
          .map((char) => `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`)
          .join('')
      );
      return JSON.parse(json);
    } catch {
      return null;
    }
  }
}
