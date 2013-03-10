;; TODO edit this with links to the software being installed and configured
(ns pallet.crate.runit
  "A [pallet](https://palletops.com/) crate to install and configure runit"
  [pallet.action :refer [with-action-options]]
  [pallet.actions :refer [directory exec-checked-script remote-directory
                          remote-file]
                  :as actions]
  [pallet.api :refer [plan-fn] :as api]
  [pallet.crate :refer [assoc-settings defmethod-plan defplan get-settings]]
  [pallet.crate-install :as crate-install]
  [pallet.stevedore :refer [fragment]]
  [pallet.script.lib :refer [config-root file]]
  [pallet.utils :refer [apply-map]]
  [pallet.version-dispatch :refer [defmethod-version-plan
                                   defmulti-version-plan]])

;;; # Settings
(defn default-settings
  "Provides default settings, that are merged with any user supplied settings."
  []
  ;; TODO add configuration options here
  {:user "runit"
   :group "runit"
   :owner "runit"
   :config-dir (fragment (file (config-root) "runit"))})

(defmulti-version-plan settings-map [version settings])

(defmethod-version-plan
    settings-map {:os :linux}
    [os os-version version settings]
  (cond
   (:install-strategy settings) settings
   :else (assoc settings
           :install-strategy :packages
           :packages {:apt ["runit"]
                      :aptitude ["runit"]
                      :yum ["runit"]
                      :pacman ["runit"]
                      :zypper ["runit"]
                      :portage ["runit"]
                      :brew ["runit"]})))

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
    (actions/group group :system true)
    (when (not= owner user)
      (actions/user owner :group group :system true))
    (actions/user
     user :group group :system true :create-home true :shell :bash)))

;;; # Install
(defplan install
  "Install runit"
  [& {:keys [instance-id]}]
  (let [settings (get-settings :runit {:instance-id instance-id})]
    (crate-install/install :runit instance-id)))

;;; # Configure
(def ^{:doc "Flag for recognising changes to configuration"}
  runit-config-changed-flag "runit-config")

(defplan config-file
  "Helper to write config files"
  [{:keys [owner group config-dir] :as settings} filename file-source]
  (directory config-dir :owner owner :group group)
  (apply
   remote-file (str config-dir "/" filename)
   :flag-on-changed runit-config-changed-flag
   :owner owner :group group
   (apply concat file-source)))

(defplan configure
  "Write all config files"
  [{:keys [instance-id] :as options}]
  (let [{:keys [] :as settings} (get-settings :runit options)]
    (config-file settings "runit.conf" {:content (str config)})))

;;; # Server spec
(defn server-spec
  "Returns a server-spec that installs and configures runit."
  [settings & {:keys [instance-id] :as options}]
  (api/server-spec
   :phases
   {:settings (plan-fn
                (pallet/crate/runit/settings (merge settings options)))
    :install (plan-fn
              (user options)
              (install :instance-id instance-id))
    :configure (plan-fn
                 (config options)
                 (run options))}))
