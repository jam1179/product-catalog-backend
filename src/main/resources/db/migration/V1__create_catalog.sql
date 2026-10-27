CREATE TABLE category (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    CONSTRAINT category_name_nonblank CHECK (length(trim(name)) > 0)
);
CREATE TABLE product (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    category_id BIGINT NOT NULL REFERENCES category(id),
    price NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT product_sku_format CHECK (sku ~ '^[A-Z0-9][A-Z0-9._-]*$'),
    CONSTRAINT product_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT product_description_length CHECK (description IS NULL OR length(description) <= 20000)
);
CREATE INDEX idx_product_category_price_id ON product(category_id, price, id);
CREATE INDEX idx_product_price_id ON product(price, id);
