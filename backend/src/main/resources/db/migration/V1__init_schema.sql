-- ============================================================
-- V1__init_schema.sql
-- Hotel Bahia / Hotel Mint - Esquema inicial completo
-- ============================================================

CREATE TABLE IF NOT EXISTS habitaciones (
    id INT AUTO_INCREMENT PRIMARY KEY,
    version BIGINT DEFAULT 0,
    numero VARCHAR(10) NOT NULL UNIQUE,
    piso INT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    capacidad INT NOT NULL DEFAULT 2,
    precio_noche DECIMAL(10, 2) NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'LIBRE',
    descripcion VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS huespedes (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    tipo_documento VARCHAR(20) NOT NULL,
    nro_documento VARCHAR(30) NOT NULL UNIQUE,
    telefono VARCHAR(255),
    email VARCHAR(255),
    nacionalidad VARCHAR(255),
    fecha_nacimiento DATE,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usuarios (
    id UUID PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(20) NOT NULL DEFAULT 'RECEPCIONISTA',
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS reservas (
    id UUID PRIMARY KEY,
    version BIGINT DEFAULT 0,
    habitacion_id INT NOT NULL,
    huesped_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    fecha_entrada DATE NOT NULL,
    fecha_salida DATE NOT NULL,
    hora_entrada_acordada TIME NOT NULL DEFAULT '15:00:00',
    hora_salida_acordada TIME NOT NULL DEFAULT '12:00:00',
    checkin_real DATETIME,
    checkout_real DATETIME,
    horas_extra INT DEFAULT 0,
    cargo_horas_extra DECIMAL(10, 2) DEFAULT 0.00,
    total_consumo DECIMAL(10, 2) DEFAULT 0.00,
    num_huespedes SMALLINT NOT NULL DEFAULT 1,
    estado VARCHAR(20) NOT NULL DEFAULT 'CONFIRMADA',
    notas VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_reservas_habitacion FOREIGN KEY (habitacion_id) REFERENCES habitaciones(id),
    CONSTRAINT fk_reservas_huesped FOREIGN KEY (huesped_id) REFERENCES huespedes(id),
    CONSTRAINT fk_reservas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS menu_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255),
    categoria VARCHAR(50) NOT NULL,
    precio DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    disponible BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME,
    updated_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pedidos (
    id UUID PRIMARY KEY,
    version BIGINT DEFAULT 0,
    reserva_id UUID NOT NULL,
    habitacion_id INT NOT NULL,
    usuario_id UUID NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    total DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    notas VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_pedidos_reserva FOREIGN KEY (reserva_id) REFERENCES reservas(id),
    CONSTRAINT fk_pedidos_habitacion FOREIGN KEY (habitacion_id) REFERENCES habitaciones(id),
    CONSTRAINT fk_pedidos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pedido_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id UUID NOT NULL,
    menu_item_id INT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10, 2) NOT NULL,
    subtotal DECIMAL(10, 2) NOT NULL,
    CONSTRAINT fk_pedido_items_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id) ON DELETE CASCADE,
    CONSTRAINT fk_pedido_items_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_items(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS pagos (
    id UUID PRIMARY KEY,
    reserva_id UUID NOT NULL,
    monto DECIMAL(10, 2) NOT NULL,
    metodo_pago VARCHAR(30) NOT NULL,
    estado_pago VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    referencia VARCHAR(255),
    fecha_pago DATETIME,
    usuario_id UUID,
    CONSTRAINT fk_pagos_reserva FOREIGN KEY (reserva_id) REFERENCES reservas(id),
    CONSTRAINT fk_pagos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS auditorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor VARCHAR(150) NOT NULL,
    accion VARCHAR(50) NOT NULL,
    entidad VARCHAR(50) NOT NULL,
    entidad_id VARCHAR(50),
    detalles TEXT,
    ip VARCHAR(45),
    created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS idempotencia_claves (
    clave VARCHAR(100) PRIMARY KEY,
    entidad VARCHAR(50) NOT NULL,
    recurso_id VARCHAR(100) NOT NULL,
    status_code INT NOT NULL,
    respuesta_json LONGTEXT,
    created_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
