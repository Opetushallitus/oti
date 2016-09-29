(ns oti.ui.app
  (:require [reagent.core :as reagent]
            [re-frame.core :as re-frame]
            [devtools.core :as devtools]
            [oti.ui.handlers]
            [oti.ui.subs]
            [oti.ui.routes :as routes]
            [oti.ui.views.virkailija-main :as virkailija]
            [oti.ui.views.hakija-main :as hakija]
            [oti.ui.config :as config]))

(defn dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")
    (devtools/install!)))

(defn mount-root [mode]
  (reagent/render (if (= "virkailija" mode)
                    [virkailija/main-panel]
                    [hakija/main-panel])
                  (.getElementById js/document "app")))

(defn ^:export start [mode]
  (routes/app-routes)
  (re-frame/dispatch-sync [:initialize-db])
  (re-frame/dispatch [:load-user])
  (dev-setup)
  (mount-root mode))

(defn reload-hook []
  (reagent/force-update-all))
