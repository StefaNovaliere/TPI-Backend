package com.tpi.orders.models;

/** Estado de una orden de compra o de venta. */
public enum EstadoOrden {
    ABIERTA,      // Orden de venta pendiente de emparejar (total o parcialmente)
    PARCIAL,      // Se ejecutó parcialmente
    COMPLETADA,   // Se ejecutó en su totalidad
    RECHAZADA     // No se pudo ejecutar (no había contraparte compatible)
}
