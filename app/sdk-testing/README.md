# app/sdk-testing

Fakes for testing plugins without the runtime: `FakeInstanceContext` plus a fake clock, API, refresher,
storage, alerts, event bus, log and instance lookup. Everything is in memory and nothing runs by itself;
the test calls `context.refresher.runAll()` to do one refresh. See the Trakt plugin's tests for an example.

Use it only as a test dependency:

```kotlin
kotlin { sourceSets { commonTest.dependencies { implementation(project(":app:sdk-testing")) } } }
```
