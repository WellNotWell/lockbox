alter table entry_fields
    add column kind         varchar(8) not null default 'TEXT',
    add column file_name    varchar(255),
    add column content_type varchar(120),
    add column size_bytes   bigint,
    add column storage_key  varchar(200);

alter table entry_fields
    alter column value drop not null,
    alter column kind drop default;

alter table entry_fields
    add constraint uq_entry_fields_storage_key unique (storage_key);

alter table entry_fields
    add constraint ck_entry_fields_kind check (
        (kind = 'TEXT' and value is not null and storage_key is null)
            or (kind = 'FILE' and storage_key is not null and file_name is not null and size_bytes is not null)
        );

drop table attachments;
