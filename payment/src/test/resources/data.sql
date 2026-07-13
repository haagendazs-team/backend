insert into subscription_plan (id, name, type, duration_days, searchable_days, status)
values
    (1, 'Standard Plan', 0, 365, 30, 0),
    (2, 'Plus Plan', 1, 30, 90, 0),
    (3, 'Pro Plan', 2, 30, 365, 0);

insert into products (id, name, product_type, price, status, product_detail_id)
values
    (1, 'Standard Subscription', 'SUBSCRIPTION', 0, 'ACVIVE', 1),
    (2, 'Plus Subscription', 'SUBSCRIPTION', 19900, 'ACVIVE', 2),
    (3, 'Pro Subscription', 'SUBSCRIPTION', 29900, 'ACVIVE', 3);

alter table subscription_plan alter column id restart with 100;
alter table products alter column id restart with 100;
