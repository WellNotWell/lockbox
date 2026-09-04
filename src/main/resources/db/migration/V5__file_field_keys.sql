alter table entry_fields
    add column data_key bytea;

delete
from entry_fields
where kind = 'FILE'
  and data_key is null;

alter table entry_fields
    drop constraint ck_entry_fields_kind;

alter table entry_fields
    add constraint ck_entry_fields_kind check (
        (kind = 'TEXT' and value is not null and storage_key is null and data_key is null)
            or (kind = 'FILE' and storage_key is not null and file_name is not null
                and size_bytes is not null and data_key is not null)
        );
