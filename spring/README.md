# confiqure-spring

Spring Boot auto-configuration for [confiqure.ai](https://confiqure.ai) host tools. It gives you a
`ConfiqureCallback` you can inject into an **asynchronous** server-side tool so the host can reply
*after* the inbound request returns — no manual reply-URL parsing, HTTP plumbing, or API-key wiring.

You only need this for `@Confiqure.Tool(async = true)` tools. A **synchronous** tool (the default,
~90% of cases) just `return`s its result and confiqure takes the response body inline — no library.

## Install

```xml
<dependency>
    <groupId>ai.confiqure</groupId>
    <artifactId>confiqure-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

Compiled against Spring Boot 3.4 with version-less, non-transitive Spring/Jackson deps, so the one
artifact runs on Boot **3.x and 4.x** hosts and never overrides your Spring versions. Requires
Spring MVC (servlet); it stays inert on a non-MVC app. (This is separate from
`ai.confiqure:confiqure-annotation-java`, which provides the `@Confiqure` annotations — add both.)

## Configure

```properties
confiqure.api-key=cqai_xxxxxxxx            # your workspace API key (Dashboard → Host integration). Keep it in an env var.
confiqure.base-url=https://api.confiqure.ai # default; set only for a self-hosted/dev confiqure
```

`confiqure.api-key` is required for replies to send. `confiqure.base-url` is a safety pin: a reply is
only sent when its `X-Confiqure-Reply-Url` starts with it, so a forged dispatch can't redirect your
API key to another host. (Env: `CONFIQURE_API_KEY`, `CONFIQURE_BASE_URL`.)

## Use

Add a `ConfiqureCallback` parameter to your async tool's handler. ACK the inbound request fast, do
the slow work off-thread, then `reply(...)`:

```java
@Confiqure.Tool(name = "analyze_supplier", async = true)
@PostMapping("/tools/analyze")
public ResponseEntity<Void> analyze(@RequestBody SupplierQuery q, ConfiqureCallback reply) {
    CompletableFuture
        .supplyAsync(() -> service.expensiveAnalysis(q))   // off the request thread
        .whenComplete((result, ex) -> {
            if (ex != null) reply.fail(ex.getMessage());
            else            reply.reply(result);            // POSTed back to confiqure for you
        });
    return ResponseEntity.accepted().build();               // ACK now; the result follows
}
```

- `reply(result)` — deliver a success (serialized to JSON).
- `reply(result, error)` — error wins when non-blank.
- `fail(error)` — deliver a failure the agent can recover from.
- `isPresent()` — `true` when the request carried the async-reply headers (i.e. it really was an
  async tool call). When `false`, `reply`/`fail` no-op with a warning instead of throwing.

The reply is **one-shot** on confiqure's side (first `reply`/`fail` wins; the correlation token
expires after ~5 min) and the POST is **fire-and-forget** (it never blocks your handler; the outcome
is logged at `INFO`/`WARN` under `ai.confiqure.spring`).

## What it does under the hood

`@Confiqure.Tool(async = true)` makes confiqure ACK your tool call immediately and send two headers
with the dispatch: `X-Confiqure-Tool-Call-Id` (a one-shot correlation token) and
`X-Confiqure-Reply-Url`. The injected `ConfiqureCallback` reads them and, on `reply`/`fail`, POSTs
`{"result": …, "error": …}` to the reply URL with `Authorization: Bearer <confiqure.api-key>` and the
token echoed back — exactly what confiqure's `/toolreply` endpoint expects.
