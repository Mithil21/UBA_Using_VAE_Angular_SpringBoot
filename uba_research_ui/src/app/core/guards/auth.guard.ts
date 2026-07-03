import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { SessionStore } from '../services/session-store.service';

export const authGuard: CanActivateFn = () => {
  const session = inject(SessionStore);
  const router  = inject(Router);
  return session.snapshot ? true : router.createUrlTree(['/login']);
};
