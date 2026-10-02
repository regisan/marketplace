-- Dados sintéticos (P-POC-03): 1.000 SKUs fictícios para o país BR, sem nenhum dado real.
-- Preço determinístico: 10,00 + (n mod 50) * 2,50. A cada 100 SKUs, um não está à venda.
INSERT INTO catalog_item_view (country, sku, price, currency, description, sellable, catalog_version)
SELECT 'BR',
       'SKU-' || lpad(n::text, 4, '0'),
       10.00 + (n % 50) * 2.50,
       'BRL',
       'Produto sintético ' || lpad(n::text, 4, '0'),
       n % 100 <> 0,
       1
FROM generate_series(1, 1000) AS n;
