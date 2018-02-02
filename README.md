# Ring-Spec

A library for [Ring][] that contains [specs][] for requests, responses
and handlers.

[ring]:  https://github.com/ring-clojure/ring
[specs]: http://clojure.org/about/spec

## Installation

Add the following dependency to your project file:

```clojure
[ring/ring-spec "0.0.4"]
```

## Usage

This library exposes the following specs:

* `:ring/request`
* `:ring/response`
* `:ring/handler`

These can be used to validate requests, responses and handler
functions respectively.

The request and response specs also have generators associated with
them, allowing valid Ring requests and responses to be generated for
use with [test.check][].

[test.check]: https://github.com/clojure/test.check

## License

Copyright © 2017 James Reeves

Distributed under the MIT License, the same as Ring.
