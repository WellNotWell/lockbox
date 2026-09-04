create table attachments
(
    id           bigserial primary key,
    entry_id     bigint       not null references entries (id) on delete cascade,
    file_name    varchar(255) not null,
    content_type varchar(120),
    size_bytes   bigint       not null,
    storage_key  varchar(200) not null,
    created_at   timestamptz  not null,
    constraint uq_attachments_storage_key unique (storage_key)
);

create index idx_attachments_entry on attachments (entry_id, created_at);
