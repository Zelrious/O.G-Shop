# Data Flow

## HTTP command

```text
Client request
  -> Controller validates shape
  -> Application service authorizes role + ownership + state
  -> Domain rule evaluates transition
  -> Repository persists in transaction
  -> Audit/notification event is recorded after business state succeeds
  -> DTO response
```

## Payment callback

```text
Provider/mock callback
  -> Verify source/signature or sandbox token
  -> Resolve idempotency key
  -> Lock current Payment/Order state
  -> Apply allowed transition once
  -> Record audit and notification
  -> Return stable acknowledgement
```

## Read flow

```text
Client query
  -> Controller parses filter/page
  -> Query service applies visibility rules
  -> Repository/projection returns allowed fields
  -> DTO response
```

Sensitive media is represented by private object keys. A short-lived signed URL is issued only after access checks.
