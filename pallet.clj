;;; Pallet project configuration file

(require
 '[pallet.crate.runit-test
   :refer [runit-test-spec]]
 '[pallet.crates.test-nodes :refer [node-specs]])

(defproject runit-crate
  :provider node-specs                  ; supported pallet nodes
  :groups [(group-spec "runit-test"
             :extends [with-automated-admin-user
                       runit-test-spec]
             :roles #{:live-test :default :runit})])
