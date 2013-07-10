[Repository](https://github.com/pallet/runit-crate) &#xb7;
[Issues](https://github.com/pallet/runit-crate/issues) &#xb7;
[API docs](http://palletops.com/runit-crate/0.8/api) &#xb7;
[Annotated source](http://palletops.com/runit-crate/0.8/annotated/uberdoc.html) &#xb7;
[Release Notes](https://github.com/pallet/runit-crate/blob/develop/ReleaseNotes.md)

A pallet crate to install and configure runit.

### Dependency Information

```clj
:dependencies [[com.palletops/runit-crate "0.8.0-alpha.1"]]
```

### Releases

<table>
<thead>
  <tr><th>Pallet</th><th>Crate Version</th><th>Repo</th><th>GroupId</th></tr>
</thead>
<tbody>
  <tr>
    <th>0.8.0-RC.1</th>
    <td>0.8.0-SNAPSHOT</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/runit-crate/blob/0.8.0-SNAPSHOT/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/runit-crate/blob/0.8.0-SNAPSHOT/'>Source</a></td>
  </tr>
  <tr>
    <th>0.8.0-beta.6</th>
    <td>0.8.0-alpha.1</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/runit-crate/blob/0.8.0-alpha.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/runit-crate/blob/0.8.0-alpha.1/'>Source</a></td>
  </tr>
</tbody>
</table>

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

## Support

[On the group](http://groups.google.com/group/pallet-clj), or
[#pallet](http://webchat.freenode.net/?channels=#pallet) on freenode irc.

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)

Copyright 2013 Hugo Duncan.
