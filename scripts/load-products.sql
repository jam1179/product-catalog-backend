-- Development/load-test database only. Assumes local categories exist.
-- Run: psql "$DB_CONNECTION_URI" -v ON_ERROR_STOP=1 -f scripts/load-products.sql
INSERT INTO product(sku, name, description, category_id, price)
SELECT 'LOAD-' || n, 'Load product ' || n, 'Generated catalog product',
       (SELECT min(id) FROM category), ((n * 37) % 1000000)::numeric / 100
FROM generate_series(1, 300000) AS n
ON CONFLICT (sku) DO NOTHING;
ANALYZE product;
-- Select actual category IDs from /api/v1/categories before EXPLAIN testing.
