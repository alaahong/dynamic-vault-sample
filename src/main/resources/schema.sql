CREATE TABLE IF NOT EXISTS sample_table (
  id SERIAL PRIMARY KEY,
  name TEXT
);

INSERT INTO sample_table (name) VALUES ('record-1') ON CONFLICT DO NOTHING;
