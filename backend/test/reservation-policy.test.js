import assert from "node:assert/strict";
import test from "node:test";

import {
  addCalendarMonths,
  assertReservationTransition,
  canCustomerCancel,
  dateInTimeZone,
  validateReservationDates
} from "../src/reservation-policy.js";

const config = {
  HOTEL_TIME_ZONE: "Europe/Madrid",
  MAX_RESERVATION_NIGHTS: 30,
  BOOKING_HORIZON_MONTHS: 18,
  CANCELLATION_NOTICE_HOURS: 48,
  CHECK_IN_HOUR_LOCAL: 15
};

test("usa la fecha civil de Madrid cerca de medianoche y durante horario de verano", () => {
  assert.equal(dateInTimeZone(new Date("2026-03-28T23:30:00Z"), config.HOTEL_TIME_ZONE), "2026-03-29");
  assert.equal(dateInTimeZone(new Date("2026-10-24T22:30:00Z"), config.HOTEL_TIME_ZONE), "2026-10-25");
});

test("rechaza pasado, estancia excesiva y horizonte excesivo", () => {
  const now = new Date("2026-08-01T10:00:00Z");
  assert.throws(() => validateReservationDates({ checkIn: "2026-07-31", checkOut: "2026-08-02" }, config, now), /pasado/);
  assert.throws(() => validateReservationDates({ checkIn: "2026-08-02", checkOut: "2026-09-02" }, config, now), /duración/);
  assert.throws(() => validateReservationDates({ checkIn: "2028-03-01", checkOut: "2028-03-02" }, config, now), /horizonte/);
  assert.equal(validateReservationDates({ checkIn: "2026-08-01", checkOut: "2026-08-02" }, config, now).nights, 1);
  assert.equal(addCalendarMonths("2025-08-31", 6), "2026-02-28");
});

test("aplica la máquina de estados y la ventana de cancelación", () => {
  const now = new Date("2026-08-01T10:00:00Z");
  assert.deepEqual(assertReservationTransition("pending", "confirmed", { checkOut: "2026-08-05" }, config, now), { idempotent: false });
  assert.throws(() => assertReservationTransition("cancelled", "confirmed", { checkOut: "2026-08-05" }, config, now), /no está permitida/);
  assert.throws(() => assertReservationTransition("confirmed", "completed", { checkOut: "2026-08-05" }, config, now), /todavía/);
  assert.equal(canCustomerCancel("2026-08-04", config, now), true);
  assert.equal(canCustomerCancel("2026-08-02", config, now), false);
});

test("calcula la cancelación contra la hora local exacta de entrada", () => {
  assert.equal(canCustomerCancel("2026-08-04", config, new Date("2026-08-02T13:00:00Z")), true);
  assert.equal(canCustomerCancel("2026-08-04", config, new Date("2026-08-02T13:00:00.001Z")), false);
});

test("la ventana de cancelación respeta los cambios de horario", () => {
  assert.equal(canCustomerCancel("2026-03-30", config, new Date("2026-03-28T13:00:00Z")), true);
  assert.equal(canCustomerCancel("2026-10-26", config, new Date("2026-10-24T14:00:00Z")), true);
});
