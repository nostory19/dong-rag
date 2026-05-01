alter table documents
    add column if not exists content_hash varchar(64);

alter table document_chunks
    add column if not exists chunk_hash varchar(64),
    add column if not exists vector_indexed boolean not null default false,
    add column if not exists es_indexed boolean not null default false;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conname = 'uk_document_chunks_doc_chunk_idx'
    ) then
        alter table document_chunks
            add constraint uk_document_chunks_doc_chunk_idx unique (document_id, chunk_index);
    end if;
end $$;

create index if not exists idx_document_chunks_doc_vector on document_chunks(document_id, vector_indexed);
create index if not exists idx_document_chunks_doc_es on document_chunks(document_id, es_indexed);
create index if not exists idx_documents_status on documents(status);
create index if not exists idx_ingestion_jobs_status_next_retry on ingestion_jobs(status, next_retry_at);
