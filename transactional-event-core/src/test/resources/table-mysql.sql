CREATE TABLE IF NOT EXISTS event_store (
	id VARCHAR(50) NOT NULL,
	event_type VARCHAR(50) NOT NULL,
	payload VARCHAR(4000) NOT NULL,
	published_at TIMESTAMP NOT NULL,
	tries INT NOT NULL,
	lock_owner VARCHAR(50),
	locked_until BIGINT NOT NULL,
	PRIMARY KEY (id)
);

DROP INDEX event_store_locked_until ON event_store;

CREATE INDEX event_store_locked_until ON event_store (locked_until);
