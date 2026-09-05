create table password_history
(
    id            bigserial primary key,
    user_id       bigint       not null references users (id) on delete cascade,
    password_hash varchar(100) not null,
    retired_at    timestamptz  not null
);

create index idx_password_history_user on password_history (user_id, retired_at desc);
