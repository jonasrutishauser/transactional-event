DROP TABLE event_store;

CREATE TABLE event_store (
	id VARCHAR2(50) NOT NULL,
	event_type VARCHAR2(50) NOT NULL,
	context VARCHAR(4000),
	payload VARCHAR2(4000) NOT NULL,
	published_at TIMESTAMP NOT NULL,
	tries NUMBER NOT NULL,
	lock_owner VARCHAR2(50),
	locked_until NUMBER NOT NULL,
	PRIMARY KEY (id)
);

DROP INDEX event_store_locked_until;

CREATE INDEX event_store_locked_until ON event_store (locked_until);
