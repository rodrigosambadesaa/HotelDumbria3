const DAY_MS = 86_400_000;

export class ReservationPolicyError extends Error {
  constructor(code, message) {
    super(message);
    this.name = "ReservationPolicyError";
    this.code = code;
    this.statusCode = 400;
  }
}

function dateParts(isoDate) {
  const [year, month, day] = isoDate.split("-").map(Number);
  return { year, month, day };
}

function utcDate(isoDate) {
  const { year, month, day } = dateParts(isoDate);
  return Date.UTC(year, month - 1, day);
}

export function nightsBetween(checkIn, checkOut) {
  return Math.round((utcDate(checkOut) - utcDate(checkIn)) / DAY_MS);
}

export function dateInTimeZone(now, timeZone) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit"
  }).formatToParts(now);
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function timeZoneOffsetMilliseconds(instant, timeZone) {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hourCycle: "h23"
  }).formatToParts(instant);
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  const representedAsUtc = Date.UTC(
    Number(value.year),
    Number(value.month) - 1,
    Number(value.day),
    Number(value.hour),
    Number(value.minute),
    Number(value.second)
  );
  return representedAsUtc - instant.getTime();
}

export function localDateHourToInstant(isoDate, hour, timeZone) {
  const { year, month, day } = dateParts(isoDate);
  const wallClockAsUtc = Date.UTC(year, month - 1, day, hour);
  let candidate = new Date(wallClockAsUtc);
  // The second pass handles estimates that cross a daylight-saving boundary.
  for (let index = 0; index < 2; index += 1) {
    candidate = new Date(wallClockAsUtc - timeZoneOffsetMilliseconds(candidate, timeZone));
  }
  return candidate;
}

export function addCalendarMonths(isoDate, months) {
  const { year, month, day } = dateParts(isoDate);
  const index = year * 12 + month - 1 + months;
  const targetYear = Math.floor(index / 12);
  const targetMonth = index % 12;
  const lastDay = new Date(Date.UTC(targetYear, targetMonth + 1, 0)).getUTCDate();
  return `${targetYear}-${String(targetMonth + 1).padStart(2, "0")}-${String(Math.min(day, lastDay)).padStart(2, "0")}`;
}

export function validateReservationDates({ checkIn, checkOut }, config, now = new Date()) {
  if (checkOut <= checkIn) {
    throw new ReservationPolicyError("INVALID_DATES", "La fecha de salida debe ser posterior a la entrada");
  }
  const today = dateInTimeZone(now, config.HOTEL_TIME_ZONE);
  if (checkIn < today) {
    throw new ReservationPolicyError("CHECK_IN_IN_PAST", "No se puede reservar en el pasado");
  }
  const nights = nightsBetween(checkIn, checkOut);
  if (nights > config.MAX_RESERVATION_NIGHTS) {
    throw new ReservationPolicyError("STAY_TOO_LONG", "La estancia supera la duración máxima");
  }
  const horizon = addCalendarMonths(today, config.BOOKING_HORIZON_MONTHS);
  if (checkIn > horizon) {
    throw new ReservationPolicyError("BOOKING_TOO_FAR", "La fecha supera el horizonte de reservas");
  }
  return { nights, today, horizon };
}

const transitions = Object.freeze({
  pending: new Set(["confirmed", "cancelled"]),
  confirmed: new Set(["completed", "cancelled"]),
  cancelled: new Set(),
  completed: new Set()
});

export function assertReservationTransition(previous, next, reservation, config, now = new Date()) {
  if (previous === next) return { idempotent: true };
  if (!transitions[previous]?.has(next)) {
    throw new ReservationPolicyError("INVALID_STATUS_TRANSITION", "La transición de estado no está permitida");
  }
  const today = dateInTimeZone(now, config.HOTEL_TIME_ZONE);
  if (next === "completed" && reservation.checkOut > today) {
    throw new ReservationPolicyError("RESERVATION_NOT_FINISHED", "La reserva todavía no puede completarse");
  }
  return { idempotent: false };
}

export function canCustomerCancel(checkIn, config, now = new Date()) {
  const checkInInstant = localDateHourToInstant(
    checkIn,
    config.CHECK_IN_HOUR_LOCAL,
    config.HOTEL_TIME_ZONE
  );
  return checkInInstant.getTime() - now.getTime()
    >= config.CANCELLATION_NOTICE_HOURS * 60 * 60 * 1000;
}
