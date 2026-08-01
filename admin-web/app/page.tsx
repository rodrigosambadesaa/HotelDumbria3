"use client";

import { FormEvent, useCallback, useEffect, useRef, useState } from "react";

const API = import.meta.env.VITE_API_URL ?? "http://localhost:8080/api/v1";

type View = "overview" | "reservations" | "rooms" | "users" | "audit";
type Summary = {
  activeReservations: number;
  arrivalsToday: number;
  activeRooms: number;
  activeUsers: number;
};
type Reservation = {
  id: string;
  checkIn: string;
  checkOut: string;
  guests: number;
  status: string;
  totalPrice: string;
  roomName: string;
  guestName: string;
  email: string;
};
type Room = {
  id: number;
  name: string;
  description: string;
  pricePerNight: string;
  areaM2: number;
  maxGuests: number;
  imageUrl?: string | null;
  active: boolean;
};
type User = {
  id: string;
  email: string;
  name: string;
  role: string;
  emailVerifiedAt?: string;
  disabledAt?: string;
  createdAt: string;
};
type Audit = {
  id: number;
  action: string;
  targetType?: string;
  targetId?: string;
  metadata: Record<string, unknown>;
  createdAt: string;
};
type Session = { accessToken: string; user: { role: string } };
type Page<T> = { data: T[]; nextCursor: string | null };

let refreshInFlight: Promise<Session> | null = null;

function readCookie(name: string) {
  if (typeof document === "undefined") return "";
  const prefix = `${name}=`;
  return document.cookie
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
    ?.slice(prefix.length) ?? "";
}

function refreshWebSession(): Promise<Session> {
  if (refreshInFlight) return refreshInFlight;
  refreshInFlight = fetch(`${API}/auth/web/refresh`, {
    method: "POST",
    credentials: "include",
    headers: { "content-type": "application/json", "x-csrf-token": readCookie("hd_csrf") },
    body: "{}"
  }).then(async (response) => {
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || "Sesión caducada");
    return payload as Session;
  }).finally(() => {
    refreshInFlight = null;
  });
  return refreshInFlight;
}

export default function AdminPortal() {
  const sessionEpoch = useRef(0);
  const [token, setToken] = useState("");
  const [role, setRole] = useState("");
  const [view, setView] = useState<View>("overview");
  const [summary, setSummary] = useState<Summary | null>(null);
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [audits, setAudits] = useState<Audit[]>([]);
  const [reservationCursor, setReservationCursor] = useState<string | null>(null);
  const [userCursor, setUserCursor] = useState<string | null>(null);
  const [auditCursor, setAuditCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const api = useCallback(async <T,>(route: string, options: RequestInit = {}): Promise<T> => {
    const send = (accessToken: string) => fetch(`${API}${route}`, {
      ...options,
      credentials: "include",
      headers: {
        "content-type": "application/json",
        ...(accessToken ? { authorization: `Bearer ${accessToken}` } : {}),
        ...options.headers
      }
    });
    let response = await send(token);
    if (response.status === 401 && token) {
      const epoch = sessionEpoch.current;
      const session = await refreshWebSession();
      if (sessionEpoch.current !== epoch) throw new Error("La sesión se ha cerrado");
      if (!["admin", "staff"].includes(session.user.role)) throw new Error("Acceso no autorizado");
      setToken(session.accessToken);
      setRole(session.user.role);
      response = await send(session.accessToken);
    }
    if (response.status === 204) return undefined as T;
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || "No se pudo completar la operación");
    return payload as T;
  }, [token]);

  const loadDashboard = useCallback(async (accessToken: string, userRole: string, expectedEpoch: number) => {
    const request = async <T,>(route: string): Promise<T> => {
      const response = await fetch(`${API}${route}`, {
        headers: { authorization: `Bearer ${accessToken}` },
        credentials: "include"
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(payload.message || "No se pudieron cargar los datos");
      return payload as T;
    };
    const [summaryData, reservationData, roomData] = await Promise.all([
      request<Summary>("/admin/summary"),
      request<Page<Reservation>>("/admin/reservations"),
      request<{ data: Room[] }>("/admin/rooms")
    ]);
    if (sessionEpoch.current !== expectedEpoch) return;
    setSummary(summaryData);
    setReservations(reservationData.data);
    setReservationCursor(reservationData.nextCursor);
    setRooms(roomData.data);
    if (userRole === "admin") {
      const [userData, auditData] = await Promise.all([
        request<Page<User>>("/admin/users"),
        request<Page<Audit>>("/admin/audit")
      ]);
      if (sessionEpoch.current !== expectedEpoch) return;
      setUsers(userData.data);
      setAudits(auditData.data);
      setUserCursor(userData.nextCursor);
      setAuditCursor(auditData.nextCursor);
    }
  }, []);

  useEffect(() => {
    const epoch = sessionEpoch.current;
    const csrf = readCookie("hd_csrf");
    if (!csrf) {
      setLoading(false);
      return;
    }
    refreshWebSession()
      .then(async (session) => {
        if (sessionEpoch.current !== epoch) return;
        setToken(session.accessToken);
        setRole(session.user.role);
        await loadDashboard(session.accessToken, session.user.role, epoch);
      })
      .catch(() => {
        if (sessionEpoch.current === epoch) setToken("");
      })
      .finally(() => {
        if (sessionEpoch.current === epoch) setLoading(false);
      });
  }, [loadDashboard]);

  async function login(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setLoading(true);
    const epoch = ++sessionEpoch.current;
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch(`${API}/auth/web/login`, {
        method: "POST",
        credentials: "include",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ email: form.get("email"), password: form.get("password") })
      });
      const session = await response.json();
      if (!response.ok) throw new Error(session.message || "No se pudo iniciar sesión");
      if (!["admin", "staff"].includes(session.user.role)) {
        throw new Error("Esta cuenta no tiene acceso al panel");
      }
      if (sessionEpoch.current !== epoch) return;
      setToken(session.accessToken);
      setRole(session.user.role);
      await loadDashboard(session.accessToken, session.user.role, epoch);
    } catch (cause) {
      if (sessionEpoch.current === epoch) {
        setError(cause instanceof Error ? cause.message : "Error de autenticación");
      }
    } finally {
      if (sessionEpoch.current === epoch) setLoading(false);
    }
  }

  async function logout() {
    sessionEpoch.current += 1;
    if (refreshInFlight) await refreshInFlight.catch(() => undefined);
    await fetch(`${API}/auth/web/logout`, {
      method: "POST",
      credentials: "include",
      headers: { "content-type": "application/json", "x-csrf-token": readCookie("hd_csrf") },
      body: "{}"
    }).catch(() => {});
    setToken("");
    setRole("");
    setSummary(null);
    setReservations([]);
    setRooms([]);
    setUsers([]);
    setAudits([]);
  }

  async function changeReservationStatus(id: string, status: string) {
    try {
      const reason = status === "cancelled"
        ? window.prompt("Indica el motivo de la cancelación (mínimo 3 caracteres):")
        : undefined;
      if (status === "cancelled" && (!reason || reason.trim().length < 3)) return;
      await api(`/admin/reservations/${id}`, {
        method: "PATCH",
        body: JSON.stringify({ status, ...(reason ? { reason: reason.trim() } : {}) })
      });
      setReservations((current) => current.map((item) => item.id === id ? { ...item, status } : item));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo actualizar la reserva");
    }
  }

  async function toggleRoom(room: Room) {
    try {
      await api(`/admin/rooms/${room.id}`, {
        method: "PUT",
        body: JSON.stringify({ ...room, active: !room.active })
      });
      setRooms((current) => current.map((item) =>
        item.id === room.id ? { ...item, active: !item.active } : item
      ));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo actualizar la habitación");
    }
  }

  async function createRoom(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const created = await api<{ id: number }>("/admin/rooms", {
        method: "POST",
        body: JSON.stringify({
          name: form.get("name"),
          description: form.get("description"),
          pricePerNight: Number(form.get("price")),
          areaM2: Number(form.get("area")),
          maxGuests: Number(form.get("guests")),
          active: true
        })
      });
      setRooms((current) => [...current, {
        id: created.id,
        name: String(form.get("name")),
        description: String(form.get("description")),
        pricePerNight: String(form.get("price")),
        areaM2: Number(form.get("area")),
        maxGuests: Number(form.get("guests")),
        active: true
      }]);
      event.currentTarget.reset();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo crear la habitación");
    }
  }

  async function deleteUser(user: User) {
    if (!window.confirm(`¿Anonimizar a ${user.name}? Se revocará su acceso y se conservará el historial de reservas sin datos personales.`)) return;
    try {
      await api(`/admin/users/${user.id}`, { method: "DELETE" });
      setUsers((current) => current.filter((item) => item.id !== user.id));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo eliminar el usuario");
    }
  }

  async function loadMore<T>(
    route: string,
    cursor: string,
    append: (update: (current: T[]) => T[]) => void,
    saveCursor: (next: string | null) => void
  ) {
    try {
      const page = await api<Page<T>>(`${route}?cursor=${encodeURIComponent(cursor)}`);
      append((current) => [...current, ...page.data]);
      saveCursor(page.nextCursor);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "No se pudo cargar la página siguiente");
    }
  }

  if (loading && !token) {
    return <main className="centered"><div className="loader" aria-label="Cargando" /></main>;
  }

  if (!token) {
    return (
      <main className="login-shell">
        <section className="brand-panel">
          <div className="brand-mark">HD</div>
          <p className="eyebrow">HOTEL DUMBRÍA</p>
          <h1>Hospitalidad con una gestión más clara.</h1>
          <p className="brand-copy">
            Reservas, habitaciones y huéspedes en un panel seguro preparado para el trabajo diario.
          </p>
          <div className="place-line"><span /> Costa da Morte · Galicia</div>
        </section>
        <section className="login-panel">
          <form className="login-card" onSubmit={login}>
            <p className="eyebrow gold">ACCESO PRIVADO</p>
            <h2>Panel de administración</h2>
            <p className="muted">Introduce las credenciales generadas por el entorno local.</p>
            <label>Email<input name="email" type="email" autoComplete="username" required /></label>
            <label>Contraseña<input name="password" type="password" autoComplete="current-password" required /></label>
            {error && <p className="error" role="alert">{error}</p>}
            <button className="primary" disabled={loading}>{loading ? "Accediendo…" : "Entrar al panel"}</button>
          </form>
        </section>
      </main>
    );
  }

  const navigation: { id: View; label: string; icon: string; adminOnly?: boolean }[] = [
    { id: "overview", label: "Resumen", icon: "⌂" },
    { id: "reservations", label: "Reservas", icon: "▣" },
    { id: "rooms", label: "Habitaciones", icon: "◇" },
    { id: "users", label: "Usuarios", icon: "◎", adminOnly: true },
    { id: "audit", label: "Auditoría", icon: "≋", adminOnly: true }
  ];

  return (
    <main className="app-shell">
      <aside>
        <div className="sidebar-brand"><span>HD</span><div>Hotel Dumbría<small>Administración</small></div></div>
        <nav>
          {navigation.filter((item) => !item.adminOnly || role === "admin").map((item) => (
            <button key={item.id} className={view === item.id ? "active" : ""} onClick={() => setView(item.id)}>
              <span>{item.icon}</span>{item.label}
            </button>
          ))}
        </nav>
        <button className="logout" onClick={logout}>Cerrar sesión</button>
      </aside>
      <section className="workspace">
        <header>
          <div><p className="eyebrow gold">GESTIÓN HOTELERA</p><h1>{navigation.find((item) => item.id === view)?.label}</h1></div>
          <div className="status"><span /> Sistema operativo</div>
        </header>
        {error && <div className="error banner" role="alert">{error}<button onClick={() => setError("")}>×</button></div>}

        {view === "overview" && summary && (
          <>
            <section className="metric-grid">
              <Metric label="Reservas activas" value={summary.activeReservations} detail="Pendientes y confirmadas" />
              <Metric label="Llegadas de hoy" value={summary.arrivalsToday} detail="Check-in previsto" />
              <Metric label="Habitaciones" value={summary.activeRooms} detail="Activas para reserva" />
              <Metric label="Usuarios" value={summary.activeUsers} detail="Cuentas habilitadas" />
            </section>
            <section className="panel">
              <div className="panel-title"><div><p className="eyebrow">PRÓXIMAS ESTANCIAS</p><h2>Reservas recientes</h2></div>
                <button className="secondary" onClick={() => setView("reservations")}>Ver todas</button></div>
              <ReservationTable reservations={reservations.slice(0, 6)} onStatus={changeReservationStatus} />
            </section>
          </>
        )}

        {view === "reservations" && (
          <section className="panel">
            <div className="panel-title"><div><p className="eyebrow">OPERACIONES</p><h2>Gestión de reservas</h2></div>
              <span className="count">{reservations.length} registros</span></div>
            <ReservationTable reservations={reservations} onStatus={changeReservationStatus} />
            {reservationCursor && <button className="secondary" onClick={() => loadMore<Reservation>(
              "/admin/reservations", reservationCursor, setReservations, setReservationCursor
            )}>Cargar más</button>}
          </section>
        )}

        {view === "rooms" && (
          <div className="split">
            <section className="panel">
              <div className="panel-title"><div><p className="eyebrow">INVENTARIO</p><h2>Habitaciones</h2></div></div>
              <div className="room-list">{rooms.map((room) => (
                <article className="room" key={room.id}>
                  <div className="room-number">{String(room.id).padStart(2, "0")}</div>
                  <div className="grow"><h3>{room.name}</h3><p>{room.areaM2} m² · hasta {room.maxGuests} huéspedes</p></div>
                  <strong>{Number(room.pricePerNight).toFixed(2)} €</strong>
                  <button className={room.active ? "pill success" : "pill"} onClick={() => toggleRoom(room)} disabled={role !== "admin"}>
                    {room.active ? "Activa" : "Inactiva"}
                  </button>
                </article>
              ))}</div>
            </section>
            {role === "admin" && <section className="panel compact">
              <p className="eyebrow">NUEVA UNIDAD</p><h2>Añadir habitación</h2>
              <form className="stack" onSubmit={createRoom}>
                <label>Nombre<input name="name" required maxLength={100} /></label>
                <label>Descripción<textarea name="description" maxLength={2000} /></label>
                <div className="form-row"><label>Precio / noche<input name="price" type="number" min="0" step="0.01" required /></label>
                  <label>Área m²<input name="area" type="number" min="1" required /></label></div>
                <label>Máximo de huéspedes<input name="guests" type="number" min="1" max="20" required /></label>
                <button className="primary">Crear habitación</button>
              </form>
            </section>}
          </div>
        )}

        {view === "users" && role === "admin" && (
          <section className="panel"><div className="panel-title"><div><p className="eyebrow">CUENTAS</p><h2>Usuarios</h2></div></div>
            <div className="table-wrap"><table><thead><tr><th>Persona</th><th>Rol</th><th>Confirmación</th><th>Alta</th><th /></tr></thead>
              <tbody>{users.map((user) => <tr key={user.id}><td><strong>{user.name}</strong><small>{user.email}</small></td>
                <td><span className="pill">{user.role}</span></td><td>{user.emailVerifiedAt ? "Confirmada" : "Pendiente"}</td>
                <td>{formatDate(user.createdAt)}</td><td><button className="danger-link" onClick={() => deleteUser(user)}>Eliminar</button></td></tr>)}</tbody></table></div>
            {userCursor && <button className="secondary" onClick={() => loadMore<User>(
              "/admin/users", userCursor, setUsers, setUserCursor
            )}>Cargar más</button>}
          </section>
        )}

        {view === "audit" && role === "admin" && (
          <section className="panel"><div className="panel-title"><div><p className="eyebrow">SEGURIDAD</p><h2>Registro de auditoría</h2></div></div>
            <div className="timeline">{audits.map((entry) => <article key={entry.id}><span /><div><strong>{entry.action}</strong>
              <p>{entry.targetType || "sistema"} {entry.targetId ? `· ${entry.targetId}` : ""}</p></div><time>{formatDateTime(entry.createdAt)}</time></article>)}</div>
            {auditCursor && <button className="secondary" onClick={() => loadMore<Audit>(
              "/admin/audit", auditCursor, setAudits, setAuditCursor
            )}>Cargar más</button>}
          </section>
        )}
      </section>
    </main>
  );
}

function Metric({ label, value, detail }: { label: string; value: number; detail: string }) {
  return <article className="metric"><p>{label}</p><strong>{value}</strong><small>{detail}</small></article>;
}

function ReservationTable({ reservations, onStatus }: {
  reservations: Reservation[];
  onStatus: (id: string, status: string) => void;
}) {
  return <div className="table-wrap"><table><thead><tr><th>Huésped</th><th>Habitación</th><th>Estancia</th><th>Total</th><th>Estado</th></tr></thead>
    <tbody>{reservations.map((item) => <tr key={item.id}><td><strong>{item.guestName}</strong><small>{item.email}</small></td>
      <td>{item.roomName}<small>{item.guests} huésped{item.guests === 1 ? "" : "es"}</small></td>
      <td>{formatDate(item.checkIn)}<small>hasta {formatDate(item.checkOut)}</small></td>
      <td><strong>{Number(item.totalPrice).toFixed(2)} €</strong></td>
      <td><select className={`state ${item.status}`} value={item.status} onChange={(event) => onStatus(item.id, event.target.value)}>
        <option value="pending">Pendiente</option><option value="confirmed">Confirmada</option>
        <option value="cancelled">Cancelada</option><option value="completed">Completada</option>
      </select></td></tr>)}</tbody></table>{!reservations.length && <p className="empty">Todavía no hay reservas.</p>}</div>;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("es-ES", { day: "2-digit", month: "short", year: "numeric" }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("es-ES", { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}
