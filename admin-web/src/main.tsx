import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import AdminPortal from "../app/page";
import "../app/globals.css";

const root = document.getElementById("root");
if (!root) throw new Error("No se encontró el contenedor principal");

createRoot(root).render(
  <StrictMode>
    <AdminPortal />
  </StrictMode>
);
