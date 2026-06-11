import { Routes } from '@angular/router';
import { RegisterComponent } from './features/auth/register/register.component';
import { LoginComponent } from './features/auth/login/login.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';

export const routes: Routes = [
  { path: '',          redirectTo: 'register', pathMatch: 'full' },
  { path: 'register',  component: RegisterComponent },
  { path: 'login',     component: LoginComponent },
  { path: 'dashboard', component: DashboardComponent },
  { path: '**',        redirectTo: 'register' },
];
