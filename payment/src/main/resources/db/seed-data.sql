insert into subscription_plan (id, name, type, duration_days, searchable_days, status)
values
    (1, 'Standard Plan', 0, 365, 30, 0),
    (2, 'Plus Plan', 1, 30, 90, 0),
    (3, 'Pro Plan', 2, 30, 365, 0)
on conflict (id) do update set
    name = excluded.name,
    type = excluded.type,
    duration_days = excluded.duration_days,
    searchable_days = excluded.searchable_days,
    status = excluded.status;

insert into products (id, name, product_type, price, status, product_detail_id)
values
    (1, 'Standard Subscription', 'SUBSCRIPTION', 0, 'ACVIVE', 1),
    (2, 'Plus Subscription', 'SUBSCRIPTION', 19900, 'ACVIVE', 2),
    (3, 'Pro Subscription', 'SUBSCRIPTION', 29900, 'ACVIVE', 3)
on conflict (id) do update set
    name = excluded.name,
    product_type = excluded.product_type,
    price = excluded.price,
    status = excluded.status,
    product_detail_id = excluded.product_detail_id;

select setval(
    pg_get_serial_sequence('subscription_plan', 'id'),
    greatest(coalesce((select max(id) from subscription_plan), 0), 99),
    true
);

select setval(
    pg_get_serial_sequence('products', 'id'),
    greatest(coalesce((select max(id) from products), 0), 99),
    true
);
