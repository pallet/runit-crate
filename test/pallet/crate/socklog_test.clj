(ns pallet.crate.socklog-test
  (:use
   clojure.test
   pallet.test-utils)
  (:require
   [pallet.actions :refer [package-manager remote-file]]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.build-actions :as build-actions]
   [pallet.crate.service :refer [service-supervisor-config]]
   [pallet.crate.service-test :refer [service-supervisor-test]]
   [pallet.crate.socklog :as socklog]
   [pallet.stevedore :refer [fragment]]))

(deftest invoke-test
  (is (build-actions/build-actions {}
        (socklog/settings {:facility :unix})
        (socklog/install {:facility :unix})
        (socklog/service :action :enable :facility :unix))))

(defn socklog-test [config]
  (service-supervisor-test :socklog config {:process-name "sleep 100"}))

(def socklog-test-spec
  (let [config {}]
    (api/server-spec
     :extends [(socklog/server-spec {:facility :unix})]
     :phases {:bootstrap (plan-fn (package-manager :update))
              :settings (plan-fn
                          (service-supervisor-config :socklog-unix config {}))
              :configure (plan-fn)
              :test (plan-fn (socklog-test config))})))
