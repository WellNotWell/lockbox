create table entries
(
    id         bigserial primary key,
    user_id    bigint       not null references users (id) on delete cascade,
    title      varchar(200) not null,
    data_key   bytea        not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);

create index idx_entries_owner on entries (user_id, title);

create table entry_fields
(
    id         bigserial primary key,
    entry_id   bigint       not null references entries (id) on delete cascade,
    label      varchar(120) not null,
    value      bytea        not null,
    secret     boolean      not null default false,
    sort_order integer      not null
);

create index idx_entry_fields_entry on entry_fields (entry_id, sort_order);
