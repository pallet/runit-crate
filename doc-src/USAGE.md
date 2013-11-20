## Usage

The `runit` configuration does not replace the system init as PID 1.

The `server-spec` function provides a convenient pallet server spec for
runit.  It takes a single map as an argument, specifying configuration
choices, as described below for the `settings` function.  You can use this
in your own group or server specs in the :extends clause.

```clj
(require '[pallet/crate/runit :as runit])
(group-spec my-runit-group
  :extends [(runit/server-spec {})])
```

While `server-spec` provides an all-in-one function, you can use the individual
plan functions as you see fit.

The `settings` function provides a plan function that should be called in the
`:settings` phase.  The function puts the configuration options into the pallet
session, where they can be found by the other crate functions, or by other
crates wanting to interact with runit.

The `install` function is responsible for actually installing runit.

The `configure` function writes the runit configuration file, using the form
passed to the :config key in the `settings` function.


### Supported Actions

The `runit` crate supports the `:start`, `:stop`, `:restart`, `:enable` and
`:disable` actions to
[`service`](http://palletops.com/pallet/api/0.8/pallet.crate.service.html#var-service).

### Job Configuration
