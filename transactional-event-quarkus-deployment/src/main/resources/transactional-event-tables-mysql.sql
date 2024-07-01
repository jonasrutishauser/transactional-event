DROP INDEX event_store_locked_until ON event_store;
DROP TABLE event_store;

CREATE TABLE event_store (
	id VARCHAR(50) NOT NULL,
	event_type VARCHAR(50) NOT NULL,
	context VARCHAR(4000),
	payload VARCHAR(4000) NOT NULL,
	published_at TIMESTAMP NOT NULL,
	tries INT NOT NULL,
	lock_owner VARCHAR(50),
	locked_until BIGINT NOT NULL,
	PRIMARY KEY (id)
);

CREATE INDEX event_store_locked_until ON event_store (locked_until);
