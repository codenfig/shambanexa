import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, catchError, of, tap } from 'rxjs';
import { AuthSession } from './models';

const TOKEN_KEY = 'shambanexa.token';
const USER_KEY = 'shambanexa.user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly session = signal<AuthSession | null>(this.read());
  private refreshed = false;

  constructor(private http: HttpClient, private router: Router) {}

  token(): string | null {
    return this.store()?.getItem(TOKEN_KEY) ?? null;
  }

  login(email: string, password: string, remember = true) {
    return this.http.post<AuthSession>('/api/v1/auth/login', { email, password }).pipe(
      tap(session => this.persist(session, remember))
    );
  }

  register(body: unknown, remember = true) {
    return this.http.post<AuthSession>('/api/v1/auth/register', body).pipe(
      tap(session => this.persist(session, remember))
    );
  }

  ensureFresh(force = false): Observable<AuthSession | null> {
    if (!this.token()) {
      return of(null);
    }
    if (force) {
      this.refreshed = false;
    }
    if (this.refreshed) {
      return of(this.session());
    }
    this.refreshed = true;
    return this.http.get<AuthSession>('/api/v1/auth/me').pipe(
      tap(session => this.persist({ ...this.normalize(session), token: this.token() }, !!localStorage.getItem(TOKEN_KEY))),
      catchError(() => {
        this.logout();
        return of(null);
      })
    );
  }

  logout(): void {
    this.refreshed = false;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_KEY);
    this.session.set(null);
    this.router.navigate(['/login']);
  }

  private persist(session: AuthSession, remember: boolean): void {
    const store = remember ? localStorage : sessionStorage;
    const other = remember ? sessionStorage : localStorage;
    other.removeItem(TOKEN_KEY);
    other.removeItem(USER_KEY);
    if (session.token) {
      store.setItem(TOKEN_KEY, session.token);
    }
    const stored = this.normalize({ ...session, token: session.token });
    store.setItem(USER_KEY, JSON.stringify(stored));
    this.session.set(stored);
  }

  private store(): Storage | null {
    if (localStorage.getItem(TOKEN_KEY)) {
      return localStorage;
    }
    if (sessionStorage.getItem(TOKEN_KEY)) {
      return sessionStorage;
    }
    return null;
  }

  private read(): AuthSession | null {
    const raw = this.store()?.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return this.normalize(JSON.parse(raw) as AuthSession);
    } catch {
      return null;
    }
  }

  private normalize(session: AuthSession): AuthSession {
    const farmIds = session.farmIds ?? [];
    const accessScope = session.accessScope
      ?? (session.allFarms ? 'ALL_FARMS' : farmIds.length <= 1 ? 'OWN_FARM' : 'SELECTED_FARMS');
    return {
      ...session,
      farmIds,
      accessScope,
      allFarms: session.allFarms || accessScope === 'ALL_FARMS',
      canManageAccess: session.canManageAccess ?? session.roleLabel === 'Super Admin',
      canWriteOperations: session.canWriteOperations ?? (session.roleLabel !== 'Auditor' && session.roleLabel !== 'Farm Adviser'),
      canAdvise: session.canAdvise ?? session.roleLabel === 'Farm Adviser'
    };
  }
}
