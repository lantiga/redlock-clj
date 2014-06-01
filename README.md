# redlock-clj

[Redlock](http://redis.io/topics/distlock) is an algorithm for distributed locks on top of a cluster of uncoordinated Redis instances. redlock-clj is a redlock implementation in Clojure leveraging on the [Carmine](https://github.com/ptaoussanis/carmine) Redis client.

[Algorithm](http://redis.io/topics/distlock) and [reference implementation](https://github.com/antirez/redlock-rb) by [Salvatore Sanfilippo](https://github.com/antirez).

## Installation

Leiningen: add the following to the `:dependencies` vector in `project.clj`

```clojure
[redlock-clj "0.1.0"]
```

## Usage

Require redlock-clj

```clojure
(require '[redlock-clj.core :as redlock])
```

Define a cluster as a vector of connection options (see [Carmine](https://github.com/ptaoussanis/carmine) for details, in particular the [docs for wcar](https://github.com/ptaoussanis/carmine/blob/master/src/taoensso/carmine.clj#L17)):

```clojure
(def cluster [{:pool {<opts1>} :spec {<opts1>}}
              {:pool {<opts2>} :spec {<opts2>}}
              {:pool {<opts3>} :spec {<opts3>}}])
```

e.g.

```clojure
(def cluster [{:spec {:host "http://127.0.0.1" :port 6379}}
              {:spec {:host "http://127.0.0.1" :port 6380}}
              {:spec {:host "http://127.0.0.1" :port 6381}}])
```

The API consists of two functions, used for locking and unlocking a resource:

```clojure
(defn lock! [cluster resource ttl & {:keys [retry-count retry-delay clock-drift-factor]}])
```

returning either a *lock* map `{:validity <validity-time> :resource <resource> :value <value>}`, where `value` is a random `uuid` created by the client acquiring the lock, or `nil` in case the lock can't be acquired;

```clojure
(defn unlock! [cluster lock])
```

which unlocks a previously acquired lock.

Look at the [test](https://github.com/lantiga/redlock-clj/blob/master/test/redlock_clj/core_test.clj) for an example of a distributed lock around a file-based counter.

Run the test with `lein test` after starting at least two out of three `redis-server` instances with ports `6379`, `6380`, `6381`.

## License

Copyright © 2014 Luca Antiga

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
