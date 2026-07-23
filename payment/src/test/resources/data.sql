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

insert into payment_customer_keys (member_id, customer_key_encrypted, customer_key_hash)
values
    (1, 'sxqub9Kwfh27bTvheC6a7q0r206+lKY7+OeJQdSbjq1vPli7rFNYdSCLDpmXRFw=', 'IzPFoNv7xAZqH1rt0YgcsBoJo0IzfuVBSnYIhJAgTuY='),
    (2, 'QaxhU9msPopOUKMmI91o4adMc8nESGNajStXoTH5fbugJgvpiLcTxGY1uzj7caA=', 'dv9NHlh90LyEHm26NzkOHGXE+aOWFFrkCGERdo2k04M='),
    (3, 'PGErU/5i2ocKB2oYkyjjFKK3YywzeT0yPnVTlKb1K70qqp3sKopgFsiW6XcGTHU=', 'uVrkAHF6Cc/nZc58JXqHRh1Xa2peUxvOTZec+uw4W+E='),
    (4, '4RIrXMt804blH6HZ0LFS6Ik1PG4nvZF6Na3UuF2x1x3vnr7C2yJ8YuK3GJOwwsE=', 'Ic7BbS/Fxi8OhbQKQYt8L6A1OnnUnaL3kvD/vdv1iiE='),
    (5, 'rl7dPykxk75nHivO0jPjHwr2gm9pRrho3mrvUIZzYRsjdbLgVOdiUylFZdYW1rY=', 'ysIkuo6pnICZSDWc8nqHf6zidaBD8NHVzYrupiMbQyU='),
    (6, 'YA/RIfwEi/bHuCiIYt/aGb5RF79NXt29MtcJZW/jTlromy+PakGKqYcQerGsSpQ=', 'X4rMnFZH2LhFcXGG9u/bhDsSvW4MxwxC458+Ti+IbHk='),
    (7, 'JsexNu+poSj4udfj4PssAM4euczUCarQes64zFP6mk0SdZNgATpmeoeCgvAxR+g=', 'SW446J41jbxbSDynWpKt/7TQldmwWCaU1xsdgUjulNg='),
    (8, 'i90VADe7jOMYuvLTCGXuq44R8t6CNrhV5Od+lTyTqEJmK/HLCYSjaeViRM+VOQ4=', 'rsBgiE6cHrc/OvC9IqfvTdbAxIakzBOb/SCiG3UQaDo='),
    (9, 'onrnttgZnRjno4X+IfEKSuq7juWdHJj/aSpgWgmIiAdjEdxjdxVvG3QpHfXO6qw=', '4Ip0sX6xHCw0JVVv/+vOTEGialIXGriF2SnZ2KgF5PA='),
    (10, 'g6u/5yLRlT7ZEzqU8LG/RSUbVpFTminQfDJrShfY3nOdQFJQA5C7q/ZSRfn3FBe5', '8kaxf9SUcYg1h+RPpkQgeYfMcbol8kKl9Qc4a+yrgfY=');

insert into billing (member_id, billing_key, issuer_code, card_number, card_type, owner_type, is_default, billing_status)
values
    (1, 'l9GjF8M-fcwHWZ0_vB3c5JvVJj5KNNNRkXHBu_zBW2g=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (2, 'muxU0nGzdAepfJWda27vETn9lOqdM0KQI7R321tf-2E=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (3, 'Ixz-33nElBmhNvhWVkiS-wPblNIetmJePkHi6pcN0ZI=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (4, 'Wt1A-pjpLJux4pBXR9TMS-Ht0ZR1T3Wa-A_0NrLXQkY=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (5, 'VzwParYLpn8UxWht3o23nHkRCPNecBpdW-PQ5SbXLOQ=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (6, 'DBMgkZpXO0APmiXVGI6hYOywEqJM_MYVgddJFBmaQFA=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (7, 'I3m5ye1IQgMKUK2ShY3XKkxN42SOz4Q28k7WxDrWxH4=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (8, 'Gk_zqIyQXEwpZU3gJrPbAxHM9V6iF0UnkmUN8K1XycE=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (9, 'VXPEa3mjYYZmsFNY0m_AlvNiOzQ6CjVrAqXzUqtzbOI=', 'KOOKMIN', '47034911****333*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE'),
    (10, 'gJNbT3srrfQaGu5Sc19zW_dtXNX5pcNX429KSONEw2A=', 'KOOKMIN', '47034911****331*', 'CREDIT', 'INDIVIDUAL', true, 'ACTIVE');

alter table subscription_plan alter column id restart with 100;
alter table products alter column id restart with 100;
alter table payment_customer_keys alter column id restart with 100;
alter table billing alter column id restart with 100;
