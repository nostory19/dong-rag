-- Align pgvector table dimension with current embedding model output (1024).
-- Existing vectors with 512 dimensions cannot be reused safely, so reset table.
drop table if exists vector_store;
