(ns pallet.crate.runit
  "A [pallet](https://palletops.com/) crate to install and configure runit.

runit is not configured to replace init as PID 1."
  (:require
   [clj-schema.schema :refer [def-map-schema optional-path sequence-of]]
   [clojure.tools.logging :refer [debugf warnf]]
   [pallet.action :refer [with-action-options]]
   [pallet.actions
    :refer [directory exec-checked-script plan-when plan-when-not
            remote-directory remote-file remote-file-arguments symbolic-link
            wait-for-file]
    :as actions]
   [pallet.actions-impl :refer [service-script-path]]
   [pallet.action-plan :as action-plan]
   [pallet.actions.direct.service :refer [service-impl]]
   [pallet.api :refer [plan-fn] :as api]
   [pallet.contracts :refer [any-value check-spec]]
   [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings
                         target-flag? update-settings]]
   [pallet.crate-install :as crate-install :refer [crate-install-settings]]
   [pallet.crate.initd :refer [init-script-path]]
   [pallet.crate.service
    :refer [service-supervisor service-supervisor-available?
            service-supervisor-config]]
   [pallet.stevedore :refer [fragment script]]
   [pallet.script.lib :refer [config-root file] :as lib]
   [pallet.utils :refer [apply-map]]
   [pallet.version-dispatch :refer [defmethod-version-plan
                                    defmulti-version-plan]]))

;;; # Settings
(def-map-schema :loose runit-settings
  crate-install-settings
  [[:user] string?
   [:group] string?
   [:owner] string?
   [:sv] string?
   [:sv-dir] string?
   [:service-dir] string?])

(defn default-settings
  "Provides default settings, that are merged with any user supplied settings."
  []
  ;; TODO add configuration options here
  {:user "runit"
   :group "runit"
   :owner "runit"
   :sv "/usr/bin/sv"
   :sv-dir (fragment (file (config-root) "sv"))
   :service-dir (fragment (file (config-root) "service"))})

(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (let [settings (update-in
                  settings [:runsvdir]
                  #(or % (fragment (file (config-root) "runit" "runsvdir"))))]
    (cond
     (:install-strategy settings) settings
     :else (assoc settings
             :install-strategy :packages
             :packages ["runit"]))))


(defmethod-version-plan
    settings-map {:os :debian-base}
    [os os-version version settings]
  (let [settings (update-in
                  settings [:runsvdir]
                  #(or % (fragment (file (config-root) "event.d" "runsvdir"))))]
    (cond
     (:install-strategy settings) settings
     :else (assoc settings
             :install-strategy :packages
             :packages ["runit"]
             :preseeds [{:line "runit runit/signalinit boolean true"}]))))

(defmethod-version-plan
    settings-map {:os :rh-base}
    [os os-version version settings]
  (let [settings (update-in
                  settings [:runsvdir]
                  #(or % (fragment (file (config-root) "runit" "runsvdir"))))]
    (cond
     (:install-strategy settings) settings
     :else (assoc settings
             :install-strategy ::build))))

(defmethod-version-plan
    settings-map {:os :os-x}
    [os os-version version settings]
  (let [settings (->
                  settings
                  (update-in
                   settings [:runsvdir]
                   #(or % (fragment
                           (file "usr" "local"  "var" "runit" "runsvdir"))))
                  (update-in
                   settings [:service-dir]
                   #(or % (fragment (file "usr" "local"  "var" "service" ))))
                  (update-in
                   settings [:sv-dir]
                   #(or % (fragment (file "usr" "local"  "var" "sv" )))))]
    (cond
     (:install-strategy settings) settings
     :else (assoc settings
             :install-strategy :packages
             :packages ["runit"]))))

(defplan settings
  "Settings for runit"
  [{:keys [instance-id] :as settings}]
  (let [settings (merge (default-settings) settings)
        settings (settings-map (:version settings) settings)]
    (assoc-settings :runit settings {:instance-id instance-id})))

;;; # User
(defplan user
  "Create the runit user"
  [{:keys [instance-id] :as options}]
  (let [{:keys [user owner group home]} (get-settings :runit options)]
    (debugf "Create runit owner %s user %s group %s" owner user group)
    (actions/group group :system true)
    (when (not= owner user)
      (actions/user owner :group group :system true))
    (actions/user
     user :group group :system true :create-home true :shell :bash)))

;;; # Install
(defplan install
  "Install runit"
  [{:keys [instance-id]}]
  (let [settings (get-settings :runit {:instance-id instance-id})]
    (debugf "Install runit settings %s" settings)
    (crate-install/install :runit instance-id)))

;;; # Configure

;;; # Service Supervisor Implementation
(defmethod service-supervisor-available? :runit
  [_]
  true)

(def-map-schema runit-service-options
  :strict
  [[:service-name] string?
   [:run-file] remote-file-arguments
   (optional-path [:log-run-file]) remote-file-arguments])

(defmacro check-runit-service-options
  [m]
  (check-spec m `runit-service-options &form))

(defn- add-service
  "Add a service directory to runit"
  [{:keys [service-name run-file] :as service-options}
   {:keys [instance-id] :as options}]
  (debugf "Adding service settings for %s" service-name)
  (check-runit-service-options service-options)
  (update-settings
   :runit options assoc-in [:jobs (keyword service-name)] service-options))

(defmethod service-supervisor-config :runit
  [_ {:keys [service-name run-file] :as service-options} options]
  (add-service service-options options))

(defn- write-service
  "Add a service directory to runit"
  [service-name
   {:keys [run-file log-run-file]}
   {:keys [instance-id] :as options}]
  (debugf "Writing service files for %s" service-name)
  (let [{:keys [sv-dir owner group]} (get-settings :runit options)]
    (directory                          ; create the service directory
     (fragment (file ~sv-dir ~service-name))
     :owner owner :group group)
    (apply-map                          ; create the run file
     remote-file (fragment (file ~sv-dir ~service-name "run"))
     :owner owner :group group
     :mode "0755"
     run-file)
    (when log-run-file
      (directory (fragment (file ~sv-dir ~service-name "log"))
                 :owner owner :group group :mode "0755")
      (apply-map                        ; create the run file
       remote-file (fragment (file ~sv-dir ~service-name "log" "run"))
       :owner owner :group group
       :mode "0755"
       log-run-file))
    ;; (directory (fragment (file ~sv-dir ~service-name "supervise"))
    ;;            :owner owner :group group :mode "0755")
    (symbolic-link                      ; link to /etc/init.d
     "/usr/bin/sv" (init-script-path service-name)
     :no-deref true)))

(defn configure
  "Write out job definitions."
  [{:keys [instance-id] :as options}]
  (let [{:keys [jobs]} (get-settings :runit {:instance-id instance-id})]
    (debugf "Writing service files for %s jobs" (count jobs))
    (doseq [[job {:keys [run-file] :as service-options}] jobs
            :let [service-name (name job)]]
      (write-service service-name service-options options))))

(def action-names
  {:reload :hup})

(defmethod service-supervisor :runit
  [_ {:keys [service-name]}
   {:keys [action if-flag if-stopped instance-id wait]
    :or {action :start wait true}
    :as options}]
  (debugf "Controlling service %s, :action %s" service-name action)
  (let [{:keys [sv sv-dir service-dir]} (get-settings :runit options)]
    (case action
      :enable (do
                (exec-checked-script
                 (format "Enable service %s" service-name)
                 (lib/ln (file ~sv-dir ~service-name)
                         (file ~service-dir ~service-name)
                         :symbolic true :force true))
                ;; Check for supervise/ok to be present. According to the docs,
                ;; this should take less than five seconds.
                (when wait
                  (wait-for-file
                   (fragment (file ~sv-dir ~service-name "supervise" "ok"))
                   :standoff 5)))
      :disable (exec-checked-script
                (format "Disable service %s" service-name)
                (sv down ~service-name)
                (lib/rm (file ~service-dir ~service-name) :force true))
      :start-stop (warnf ":start-stop not implemented for runit")
      (if if-flag
        (plan-when (target-flag? if-flag)
          (exec-checked-script
           (str (name action) " " service-name)
           (sv ~(name (action action-names action)) ~service-name)))
        (if if-stopped
          (exec-checked-script
           (str (name action) " " service-name)
           (if-not ("sv" "status" ~service-name)
             (sv ~(name (action action-names action)) ~service-name)))
          (exec-checked-script
           (str (name action) " " service-name)
           (sv ~(name (action action-names action)) ~service-name)))))))

;;; ## Server Spec
(defn server-spec
  "Returns a server-spec that installs and configures runit."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn
                (pallet.crate.runit/settings (merge settings options)))
    :install (plan-fn
               (user options)
               (install options))
    :configure (plan-fn
                 (configure options))}))
