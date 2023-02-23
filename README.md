# Transactional Event Library for Jakarta EE 9

A [Transactional Event Library](https://jonasrutishauser.github.io/transactional-event/) that implements the [outbox pattern](https://microservices.io/patterns/data/transactional-outbox.html) for Jakarta EE 9.

[![GNU Lesser General Public License, Version 3, 29 June 2007](https://img.shields.io/github/license/jonasrutishauser/transactional-event.svg?label=License)](http://www.gnu.org/licenses/lgpl-3.0.txt)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.jonasrutishauser/transactional-event-api.svg?label=Maven%20Central)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.github.jonasrutishauser%22%20a%3A%22transactional-event-api%22)
[![Build Status](https://img.shields.io/github/actions/workflow/status/jonasrutishauser/transactional-event/ci.yml.svg?label=Build)](https://github.com/jonasrutishauser/transactional-event/actions)
[![Coverage](https://img.shields.io/codecov/c/github/jonasrutishauser/transactional-event/master.svg?label=Coverage)](https://codecov.io/gh/jonasrutishauser/transactional-event)

## Used Jakarta EE APIs
The following APIs are required:
- CDI 3.0
- Concurrency Utilities 2.0
- JDBC 4.2
- JTA 2.0

The following APIs are optionally supported for serialization:
- JAXB 3.0
- JSON-B 2.0

## Publish an Event
An Event can be published using the [`EventPublisher`](https://jonasrutishauser.github.io/transactional-event/snapshot/transactional-event-api/apidocs/?com/github/jonasrutishauser/transactional/event/api/EventPublisher.html) API:

```java
   @Inject
   private EventPublisher publisher;
   
   public void someMethod() {
      ...
      SomeEvent event = ...
      publisher.publish(event);
      ...
   }
```

## Handle an Event
For every event type published there must be a corresponding [`Handler`](https://jonasrutishauser.github.io/transactional-event/snapshot/transactional-event-api/apidocs/?com/github/jonasrutishauser/transactional/event/api/handler/Handler.html) (qualified by [`EventHandler`](https://jonasrutishauser.github.io/transactional-event/snapshot/transactional-event-api/apidocs/?com/github/jonasrutishauser/transactional/event/api/handler/EventHandler.html)):

```java
@Dependent
@EventHandler
class SomeEventHandler extends AbstractHandler<SomeEvent> {
   @Override
   protected void handle(SomeEvent event) {
      ...
   }
}
```

## Data Source
The library expects that the following table exists when using the `javax.sql.DataSource` with the [`Events`](https://jonasrutishauser.github.io/transactional-event/snapshot/transactional-event-api/apidocs/?com/github/jonasrutishauser/transactional/event/api/Events.html) qualifier:

```sql
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
```

The required `javax.sql.DataSource` can be specified like the following:

```java
@Dependent
class EventsDataSource {
   @Events
   @Produces
   @Resource(name = "someDb")
   private DataSource dataSource;
}
```
## Metrics (if mpMetrics is available)
If mpMetrics is enabled on the server there will be the following metrics
- application_com_github_jonasrutishauser_transaction_event_published_total (number of published events)
- application_com_github_jonasrutishauser_transaction_event_failedattempts_total (these are the number of failed attempts to process an event)
- application_com_github_jonasrutishauser_transaction_event_success_total (these are the number of successfully processed events)
- application_com_github_jonasrutishauser_transaction_event_blocked_total (these are the number of blocked events because the maximum number of retries has been reached)
- application_com_github_jonasrutishauser_transaction_event_unblocked_total (these are the number of unblocked events)
- application_com_github_jonasrutishauser_transaction_event_deleted_total (these are the number of deleted events)
- application_com_github_jonasrutishauser_transaction_event_processing (the number of events being processed currently in total)
- application_com_github_jonasrutishauser_transaction_event_dispatched_processing (the number of dispatched events by a timer being processed currently. This metric can be used for fine-tuning transactional.event.maxConcurrentDispatching and transactional.event.maxAquire)
- application_com_github_jonasrutishauser_transaction_event_all_in_use_interval (interval between lookups for events to process when maxConcurrentDispatching is reached)
- application_com_github_jonasrutishauser_transaction_event_max_dispatch_interval (maximum interval between lookups for events to process)
- application_com_github_jonasrutishauser_transaction_event_initial_dispatch_interval (initial interval between lookups for events to process)
- application_com_github_jonasrutishauser_transaction_event_max_aquire (maximum number of events aquired per query)
- application_com_github_jonasrutishauser_transaction_event_max_concurrent_dispatching (maximum number of dispatched events being processed concurrently)
- application_com_github_jonasrutishauser_transaction_event_dispatch_interval (interval between lookups for events to process)
