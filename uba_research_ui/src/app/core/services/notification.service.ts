import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

export type NotificationType = 'success' | 'error' | 'info';

export interface Notification {
  id: string;
  type: NotificationType;
  message: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private _notifications = new BehaviorSubject<Notification[]>([]);
  notifications$ = this._notifications.asObservable();

  private show(type: NotificationType, message: string): void {
    const id = crypto.randomUUID();
    this._notifications.next([...this._notifications.value, { id, type, message }]);
    setTimeout(() => this.dismiss(id), 4000);
  }

  success(message: string): void { this.show('success', message); }
  error(message: string):   void { this.show('error',   message); }
  info(message: string):    void { this.show('info',    message); }

  dismiss(id: string): void {
    this._notifications.next(this._notifications.value.filter(n => n.id !== id));
  }
}
