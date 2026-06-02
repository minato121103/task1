import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { from, switchMap } from 'rxjs';
import { KeycloakService } from '../services/keycloak.service';

const BACKEND_ORIGIN = 'http://localhost:8080';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  if (!req.url.startsWith(BACKEND_ORIGIN) || req.url.startsWith(`${BACKEND_ORIGIN}/api/auth/`)) {
    return next(req);
  }

  const keycloakService = inject(KeycloakService);

  return from(keycloakService.getValidToken()).pipe(
    switchMap((token) => {
      if (!token) {
        return next(req);
      }

      return next(req.clone({
        setHeaders: {
          Authorization: `Bearer ${token}`
        }
      }));
    })
  );
};
