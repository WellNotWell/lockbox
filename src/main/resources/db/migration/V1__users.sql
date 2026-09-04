create table users
(
    id            bigserial primary key,
    username      varchar(64)  not null,
    password_hash varchar(100) not null,
    key_salt      bytea        not null,
    created_at    timestamptz  not null
);

create unique index uq_users_username on users (lower(username));
