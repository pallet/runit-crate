(ns pallet.crate.runit-test
  (:use
   clojure.test
   pallet.test-utils)
  (:require
   [pallet.actions :refer [package-manager remote-file]]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.build-actions :as build-actions]
   [pallet.crate.service :refer [service-supervisor-config]]
   [pallet.crate.service-test :refer [service-supervisor-test]]
   [pallet.crate.runit :as runit]
   [pallet.stevedore :refer [fragment]]))

(deftest invoke-test
  (is (build-actions/build-actions {}
        (runit/settings {})
        (runit/install))))

(defn runit-test [config]
  (service-supervisor-test :runit config {:process-name "sleep 100"}))

(def runit-test-spec
  (let [config {:service-name "myjob"
                :run-file {:content (str "#!/bin/sh\nexec /tmp/myjob")}}]
    (api/server-spec
     :extends [(runit/server-spec {})]
     :phases {:bootstrap (plan-fn (package-manager :update))
              :settings (plan-fn (service-supervisor-config :runit config {}))
              :configure (plan-fn
                           (remote-file
                            "/tmp/myjob"
                            :content (fragment ("exec" "sleep" 100000000))
                            :mode "0755"))
              :test (plan-fn (runit-test config))})))
