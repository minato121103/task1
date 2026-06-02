import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { KeycloakService } from './services/keycloak.service';

interface UserInfo {
  username: string;
  fullName: string;
  email: string;
  position: string;
  roles: string[];
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit {
  protected readonly keycloakService = inject(KeycloakService);
  private readonly http = inject(HttpClient);

  protected readonly isLoggedIn = signal(false);
  protected readonly userProfile = signal<UserInfo | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.isLoggedIn.set(this.keycloakService.isLoggedIn());

    if (this.isLoggedIn()) {
      this.fetchUserProfile();
    }
  }

  fetchUserProfile(): void {
    this.loading.set(true);
    this.error.set(null);

    this.http.get<UserInfo>('http://localhost:8080/api/users/me').subscribe({
      next: (data) => {
        this.userProfile.set(data);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Cannot load user profile from backend:', err);
        this.userProfile.set({
          ...this.keycloakService.getCurrentUser(),
          position: 'Backend unavailable',
          roles: []
        });
        this.error.set('Cannot load position from backend. Check Spring Boot, database, and Keycloak JWT config.');
        this.loading.set(false);
      }
    });
  }

  login(): void {
    this.keycloakService.login();
  }

  async logout(): Promise<void> {
    await this.keycloakService.logout();
    this.isLoggedIn.set(false);
    this.userProfile.set(null);
  }
}
